(ns pixelsquiz.core
  (:gen-class))

(require '[pixelsquiz.util :refer :all])
(require '[pixelsquiz.stage :refer :all])
(require '[pixelsquiz.types])
(import '(pixelsquiz.types Event Answer Question Round Team GameState))

(require '[reduce-fsm :as fsm])
(require '[clojure.core.async :as async
           :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                    alts! alts!! timeout]])
(require '[clojure.edn :as edn])

(require '[clojure.tools.nrepl.server :as repl])

(require 'spyscope.core)
(require '[alex-and-georges.debug-repl :refer :all])

(def buzz-score-modifier 2)

(def game-state-file "game-state.edn")
(def questions-db "questions.edn")

(def timer-active (atom false))

(defn run-timer
  [duration evkind disp c]
  (reset! timer-active true)
  (>!! disp (Event. :timer-start {}))
  (go-loop
    [seconds (dec duration)]
    (if (> seconds -1)
      (do
        (<! (timeout 1000))
        (>! disp (Event. :timer-update {:value seconds}))
        (recur (if @timer-active 
                 (dec seconds)
                 -1))
        )
      (do
        (reset! timer-active false)
        (>! c (Event. evkind {})))
      )))


(defmacro w-m-d
  [form]
  `(>!! (-> ~'world  :stage :displays) ~form))


(defn buzz-timer
  [c world & _]
  (run-timer 20 :buzz-timeout (-> world :stage :displays) c)
  world)

(defn options-timer
  [c world & _]
  (run-timer 20 :options-timeout (-> world :stage :displays) c)
  world)


(defn show-question
  [world & _]
  (w-m-d (Event. :show-question (-> world :current-question)))
  world)

(defn show-question-results
  [world & _]
  (reset! timer-active false)
  (w-m-d #spy/d (Event. :show-question-results (:current-answer world)))
  false)

(defmacro with-answer 
  [& forms]
  `(let [{question :question team-buzzed :team-buzzed good-buzz :good-buzz answers :answers scores :scores} (:current-answer ~'world)]
     ~@forms))


(defn acc-answer
  [world event & _]
  (with-answer world
    (assoc world :current-answer 
           (case (:kind event) ; XXX this is kinda ugly ...
             :buzz-pressed (Answer. question (-> event :bag-of-props :team) nil answers scores)
             :select-right (Answer. question team-buzzed true answers (assoc [0 0 0 0] team-buzzed 
                                                                             (* buzz-score-modifier (:score question))))
             :select-wrong (Answer. question team-buzzed false answers scores)))))


(defn wake-up-quizmaster
  [world event from-state to-state]
  (acc-answer world event))

(defn all-teams-answered?
  [answering-team current-answer]
  (= 4 (count (set (filter #(not (nil? %)) (conj (filter #(not (nil? (get (:answers current-answer) %))) (range 4))
                                             answering-team (:team-buzzed current-answer))))))
  )

(defn acc-option
  [timeout-chan world event & _]
  (let [question-scores (mapv #(:score %) (-> world :current-answer :question :shuffled-options))
        answering-team  (-> event :bag-of-props :team)
        selected-option  (-> event :bag-of-props :button-index)
        ]
    (when (all-teams-answered? answering-team (:current-answer world))
      (>!! timeout-chan (Event. :all-pressed {}))
      )
    (if (or (= answering-team (-> world :current-answer :team-buzzed))
            (not (nil? (get (-> world :current-answer :answers) answering-team))))
      world ; if the team buzzed ignore them
      (with-answer (assoc world :current-answer (Answer. question team-buzzed good-buzz 
                                                               (assoc answers answering-team selected-option)
                                                               (assoc scores answering-team (get question-scores selected-option)))))
    )))

(defn qm-choice
  [world event & _]
  (reset! timer-active false)
  (with-answer world 
    (w-m-d (Event. :qm-choice 
                   {:team team-buzzed
                    :right-wrong (:kind event)
                    })))
  (acc-answer world event)
  )


(defn options-show-and-timeout
  [c world & _]
  (w-m-d (Event. :show-options (:current-answer world)))
  (options-timer c world)
  world)

(defn fsm-fn
  [world event from-state to-state]
  
  world)

(defn question-on-quizmaster
  [world]
  (w-m-d (Event. :for-quizmaster {:text (str "Q:" (-> world :current-question :text) " A: " (get (-> world :current-question :options) 0))} ))
  (w-m-d (Event. :question-starting {}))
  false)

(defn right-or-wrong
  [world]
  (w-m-d (Event. :buzzed (:current-answer world)))
  false)

(defn wait-answers
  [world]
  (w-m-d (Event. :update-lights (:current-answer world) ))
  false)

(defn end-of-round
  [world]
  (w-m-d (Event. :end-of-round (:current-round world)))
  false)


(defn round-tied?
  [round]
  (let [teamscores (sort-teams-by-scores (:scores round))]
   (= (:score (first teamscores)) (:score (second teamscores)))
  ))

(defn add-tiebreaker-question-if-necessary
  [world]
  (let [current-round (:current-round world)
        question-number (get (:questions current-round) (:question-index current-round))
        ]
    (if (and (nil? question-number) (round-tied? current-round))
      (assoc world :current-round (assoc current-round :questions (conj (:questions current-round) (first (:tiebreaker-pool world))))
             :tiebreaker-pool (rest (:tiebreaker-pool world)))
      world
      )
    ))


(defn prepare-for-next-question
  [world event & _]
  (let [question-index (+ 1 (-> world :current-round :question-index))
        current-round (:current-round world)
        new-world (assoc world 
                         :answers (conj (:answers world) (:current-answer world))
                         :current-round (assoc current-round :scores (mapv + (:scores current-round) 
                                                                          (-> world :current-answer :scores))
                                                              :question-index question-index)
                         )
        ]
    (w-m-d (Event. :update-scores (:current-round new-world)))
    (w-m-d (Event. :show-question {:text ""}))
    (add-tiebreaker-question-if-necessary new-world)))

(defn start-question 
  [answer-chan world event from-state to-state]
  (let [current-round (:current-round world)
        question-index (:question-index current-round)
        question-number (get (:questions current-round) question-index)
        question (get (:questions-repo world) question-number)
        shuffled-options (shuffle (mapv (fn [text original score]
                                         {:text text
                                          :original-pos original
                                          :score score})
                                       (:options question) (range 4) [(:score question) 0 0 0] ; XXX option scores 
                                       ))
        ]
    (if (nil? question)
      (>!! answer-chan (Event. :out-of-questions {:question-index question-index})))
    (assoc world 
           :current-question question
           :current-answer (Answer. (assoc question :shuffled-options shuffled-options) nil nil [nil nil nil nil] [0 0 0 0])
           )))

(defn prepare-for-next-round
  [world event & _] 
    (assoc world
           :past-rounds (conj (:rounds world) (:current-round world)))
  )
(defn round-setup 
  [world event from-state to-state]
  (let [new-round-number (+ 1 (:round-index world))
        new-round (get (:rounds world) new-round-number)]
    (w-m-d (Event. :show-question {:text (str "Starting round " (inc new-round-number))}))
    (w-m-d (Event. :update-scores {:scores [0 0 0 0] :questionnum 0}))
    (assoc world
           :round-index new-round-number
           :current-round (assoc new-round :question-index 0))
    ))



(defn game-loop
  [game-state world]
  (let [timeout-chan (chan 16)
        stage (:stage world)
        round-events (async/merge [(:buttons stage) (:quizmaster stage) timeout-chan])
        game (fsm/fsm-inc [
                           [:start {}
                            {:kind :start-round} -> {:action round-setup} :wait-for-question]
                           [:wait-for-question {}
                            {:kind :start-question} -> {:action (partial start-question timeout-chan)} question-on-quizmaster]
                           [question-on-quizmaster {}
                            {:kind :out-of-questions} -> end-of-round
                            {:kind :show-question} -> {:action (partial buzz-timer timeout-chan)} :wait-buzz]
                           [:wait-buzz {}
                            {:kind :show-question} -> {:action show-question} :wait-buzz
                            {:kind :buzz-timeout} ->  :wait-before-options
                            {:kind :buzz-pressed} -> {:action acc-answer} right-or-wrong]
                           [right-or-wrong {}
                            {:kind :select-right} -> {:action qm-choice} show-question-results
                            {:kind :select-wrong} -> {:action qm-choice} :wait-before-options]
                           [:wait-before-options {}
                            {:kind :start-mult} -> {:action (partial options-show-and-timeout timeout-chan)} wait-answers]
                           [wait-answers {}
                            {:kind :option-pressed} -> {:action (partial acc-option timeout-chan)} wait-answers
                            {:kind :options-timeout} -> show-question-results
                            {:kind :all-pressed} -> show-question-results]
                           [show-question-results {}
                            {:kind :start-question} -> {:action prepare-for-next-question} :wait-for-question]
                           [end-of-round {}
                            {:kind :start-round} -> {:action prepare-for-next-round} :start]
                           ])
        ]
    (loop [f (game (:state @game-state) 
                   (merge (:value @game-state) 
                          world
                          {:past-rounds []
                           :answers []}
                          ))]
      (reset! game-state f)
      (recur (fsm/fsm-event f #spy/d (<!! round-events))))
  ))


(defn read-from-file
  [what]
  (case what
    :items (merge (read-string (slurp "round-config.edn"))
                  {:questions-repo (read-string (slurp questions-db))})
    :initial-state (let [saved (try 
                          (read-string (slurp game-state-file))
                          (catch Exception e {:state :start :value {:round-index -1}}))
                         state-fn (ns-resolve *ns* (symbol (name (:state saved))))]
                         (if (ifn? state-fn)
                           (assoc saved :state (deref state-fn))
                           saved))
    ))


(def game-state (atom (read-from-file :initial-state) ))

(defn save-game-state-to-file!
  [key ref old-state state]
  (let [value (:value state)]
    (spit game-state-file (pr-str {:state (:state state)
                                   :value (select-keys value [:current-question :current-answer :current-round :round-index]) 
                                   }) :append false)
    (spit (str game-state-file "-log") (pr-str state) :append true)
    (clojure.pprint/pprint (:state state))
    (clojure.pprint/pprint (select-keys value [:current-round :current-answer]))
    ))

(defn -main
  [& args]
  (let [
        game-items (read-from-file :items)
        stage (setup-stage) 
        ]
    (add-watch game-state nil save-game-state-to-file!)
    (game-loop game-state (assoc game-items :stage stage))
   )
  )


;; evil stuff here -> helpers for OMFG on the repl
(defonce server (repl/start-server :port 7888))

(defn stage-ch 
  []
  (-> @game-state :value :stage :displays))

(defn push-to-stage 
  [title options] 
  (let [world (:value @game-state)] 
    (w-m-d {:kind :show-options 
            :bag-of-props {:question {:text title 
                                      :shuffled-options (mapv #(assoc {} :text %) options)}}})
    ))

(defn team-numbers
  []
  (let [world (:value @game-state)]
    (w-m-d {:kind :team-number} 
    )))

(defn thank-sponsors
  []
  (push-to-stage "THANKS TO" ["GitHub", "Whitesmith", "Talkdesk", "and all the participants"]))

(defn list-teams
  []
  (push-to-stage "" (-> @game-state :value :current-round :teams)))


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


(defn buzz-timer 
  [c world & _]
  (go
    (<! (timeout 20000))
    (>! c (Event. :buzz-timeout {})))
  world
  )


(defn options-timeout 
  [c world & _]
  (go
    (<! (timeout 20000))
    (>! c (Event. :options-timeout {})))
  world)


(defmacro w-m-d
  [form]
  `(>!! (-> ~'world  :stage :displays) ~form))


(defn buzz-timedout
  [world & _]
  (w-m-d (Event. :timeout {}))
  world)


(defn show-question
  [world & _]
  (w-m-d (Event. :show-question (-> world :current-question)))
  world)

(defn show-question-results
  [world & _]
  (w-m-d #spy/d (Event. :show-question-results (:current-answer world)))
  false)

(defmacro with-answer 
  [& forms]
  `(let [{question :current-question team-buzzed :team-buzzed good-buzz :good-buzz answers :answers scores :scores} (:current-answer ~'world)]
     ~@forms))


(defn acc-answer
  [world event & _]
  (with-answer
    (assoc world :current-answer 
           (case (:kind event) ; XXX this is kinda ugly ...
             :option-pressed (Answer. question team-buzzed good-buzz (assoc answers (-> event :bag-of-props :team) 
                                                                       (-> event :bag-of-props :button-index)) 
                                      scores)
             :buzz-pressed (Answer. question (-> event :bag-of-props :team) good-buzz answers scores)
             :select-right (Answer. question team-buzzed true answers (assoc [0 0 0 0] (-> event :bag-of-props :team) 
                                                                             (* buzz-score-modifier ((:score question)))))
             :select-wrong (Answer. question team-buzzed false answers scores)))))


(defn wake-up-quizmaster
  [world event from-state to-state]
  (acc-answer world event))

(defn acc-option
  [timeout-chan world event & _]
  (let [new-world (acc-answer world event)
        answers (-> new-world :current-answer :answers)
        scores (map #(:score %) (-> new-world :current-answer :current-question :shuffled-options))
        ]
    (if (= 4 (count (filter #(not (nil? %)) (-> new-world :answer :answers))))
      (>!! timeout-chan (Event. :all-pressed {}))
      )
    (assoc (-> new-world :current-answer :scores) (map #(if (get answers %) (get scores (get answers %)) 0) (range 4)))
    ))




(defn options-show-and-timeout
  [c world & _]
  (w-m-d (Event. :show-options (:current-answer world)))
  (options-timeout c world)
  world)

(defn fsm-fn
  [world event from-state to-state]
  
  world)

(defn question-on-quizmaster
  [world]
  (w-m-d (Event. :for-quizmaster {:text (str "Q:" (-> world :current-question :text) " A: " (get (-> world :current-question :options) 0))} ))
  false)

(defn right-or-wrong
  [world]
  (w-m-d (Event. :for-quizmaster {:text "waiting for quizmaster right wrong"}))
  (w-m-d (Event. :buzzed (:current-answer world)))
  false)

(defn wait-answers
  [world]
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
  (let [question-index (+ 1 (:question-index world))
        new-world (assoc world 
                         :answers (conj (:answers world) (:current-answer world))
                         :question-index question-index
                         )
        ]
    (add-tiebreaker-question-if-necessary new-world)))

(defn start-question 
  [answer-chan world event from-state to-state]
  (let [current-round (:current-round world)
        question-index (:question-index world)
        question-number (get (:questions current-round) question-index)
        question (get (:questions-repo world) question-number)
        shuffled-options (shuffle (map (fn [text original score]
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
           :current-answer (Answer. (assoc question :shuffled-options shuffled-options) nil false [nil nil nil nil] [0 0 0 0])
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
    (assoc world
           :round-index new-round-number
           :current-round new-round
           :question-index 0)))

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
                            {:kind :buzz-timeout} -> {:action buzz-timedout} :wait-before-options
                            {:kind :buzz-pressed} -> {:action acc-answer} right-or-wrong]
                           [right-or-wrong {}
                            {:kind :select-right} -> {:action acc-answer} show-question-results
                            {:kind :select-wrong} -> {:action acc-answer} :wait-before-options]
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
    :items {
            :rounds [
                     (Round. 1 ['a 'b 'c 'd] [1] [0 0 0 0])
                     (Round. 2 ['e 'f 'g 'h] [1 10 11 12 13 14 15] [0 0 0 0])
                     ]
            :questions-repo [ nil
                        (Question. 1 :multi 1 "The first question" 
                                   [
                                    "First answer"
                                    "Second answer"
                                    "Third answer"
                                    "Last answer"
                                  ])
                        ]
            :tiebreaker-pool [1 1 1 1 1]
            }
    :initial-state {:state :start :value {:round-index -1}}
    ))


(def game-state (atom (read-from-file :initial-state) ))
;(defonce server (repl/start-server :port 7888))

(defn -main
  [& args]
  (let [
        game-items (read-from-file :items)
        stage (setup-stage) 
        ]
    (add-watch game-state nil (fn [k r os s] (clojure.pprint/pprint  s)))
    (game-loop game-state (assoc game-items :stage stage))
   )
  )

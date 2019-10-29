(ns pixelsquiz.core
  (:gen-class))

(require '[pixelsquiz.util :refer [sort-teams-by-scores]])
(require '[pixelsquiz.stage :refer [setup-stage]])
(require '[pixelsquiz.logger :as logger])
(require '[pixelsquiz.types])

(require '[clojure.core.async :as async :refer [>! <! >!! <!!]])
(require '[clojure.pprint :refer [pprint]])
(require '[clojure.string :refer [join starts-with?]])
(require '[reduce-fsm :as fsm])
(require '[org.httpkit.client :as http])
(require '[nrepl.server :as repl])

(import '[pixelsquiz.types Event Answer])


(def game-channel (async/chan 16))
(def buzz-score-modifier 2)

(def game-state-file "game-state.edn")
(def questions-db "questions.edn")
(def config-file "round-config.edn")

(def timer-active (atom false))

;; Emergency patches for the game state (see the "omg-*" helper functions)...
(def append-question (atom false))
(def score-adjustments (atom [0 0 0 0]))

(defn run-timer
  [duration type displays-channel notify-channel]
  (reset! timer-active true)
  (>!! displays-channel (Event. :timer-start {}))
  (>!! displays-channel (Event. :timer-update {:value duration}))
  (async/go-loop
    [seconds (dec duration)]
    (if (> seconds -1)
      (do
        (<! (async/timeout 1000))
        (if @timer-active  ;; ...might have stopped in the meanwhile.
          (do
            (>! displays-channel (Event. :timer-update {:value seconds}))
            (recur (if @timer-active (dec seconds) -1))
          )))
      (do
        (reset! timer-active false)
        (>! notify-channel (Event. type {})))
      )))


(defmacro w-m-d
  [world form]
  `(>!! (-> ~world :stage :displays) ~form))


(defn buzz-timer
  [world & _]
  (run-timer 20 :buzz-timeout (-> world :stage :displays) game-channel)
  world)

(defn options-timer
  [world & _]
  (run-timer 20 :options-timeout (-> world :stage :displays) game-channel)
  world)


(defn show-question
  [world & _]
  (w-m-d world (Event. :show-question (-> world :current-question)))
  world)

(defn show-question-results
  [world & _]
  (reset! timer-active false)
  (w-m-d world (Event. :show-question-results (:current-answer world)))
  false)

(defn acc-answer
  [world event & _]
  (let [{question :question
         team-buzzed :team-buzzed
         answers :answers
         scores :scores} (:current-answer world)]
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

(defn accumulate-options
  [world event & _]
  (let [question-scores (mapv #(:score %) (-> world :current-answer :question :shuffled-options))
        answering-team  (-> event :bag-of-props :team)
        selected-option  (-> event :bag-of-props :button-index)
        ]
    (when (all-teams-answered? answering-team (:current-answer world))
      (>!! game-channel (Event. :all-pressed {})))
    (if (or (= answering-team (-> world :current-answer :team-buzzed))
            (not (nil? (get (-> world :current-answer :answers) answering-team))))
      world ; if the team buzzed ignore them
      (let [{question :question
             team-buzzed :team-buzzed
             good-buzz :good-buzz
             answers :answers
             scores :scores} (:current-answer world)]
        (assoc world :current-answer (Answer. question team-buzzed good-buzz
                                       (assoc answers answering-team selected-option)
                                       (assoc scores answering-team (get question-scores selected-option)))))
    )))

(defn qm-choice
  [world event & _]
  (reset! timer-active false)
  (w-m-d world (Event. :qm-choice {:team (get-in world [:current-answer :team-buzzed])
                                   :right-wrong (:kind event)}))
  (acc-answer world event))


(defn options-show-and-timeout
  [world & _]
  (w-m-d world (Event. :show-options (:current-answer world)))
  (options-timer world)
  world)

(defn fsm-fn
  [world event from-state to-state]

  world)

(defn question-on-quizmaster
  [world]
  (w-m-d world (Event. :for-quizmaster {:question (-> world :current-question :text)
                                        :answer (get (-> world :current-question :options) 0)
                                        :trivia (-> world :current-question :trivia)}))
  (w-m-d world (Event. :question-starting {}))
  false)

(defn right-or-wrong
  [world]
  (reset! timer-active false)  ;; ...the quizmaster allows out-of-time answers anyway!
  (w-m-d world (Event. :buzzed (:current-answer world)))
  false)

(defn wait-answers
  [world]
  (w-m-d world (Event. :update-lights (:current-answer world) ))
  false)

(defn end-of-round
  [world]
  (w-m-d world (Event. :end-of-round (:current-round world)))
  false)


(defn round-tied?
  [round]
  (let [teamscores (sort-teams-by-scores (:scores round))]
   (= (:score (first teamscores)) (:score (second teamscores)))
  ))

(defn add-tiebreaker-question-if-necessary
  [world]
  (let [current-round (:current-round world)
        round-number (get-in world [:current-round :number])
        question-number (get (:questions current-round) (:question-index current-round))
        tiebreaker-index (first (:tiebreaker-pool world))
        questions-repo (:questions-repo world)]
    (if (and (> (count (:tiebreaker-pool world)) 0) (nil? question-number) (round-tied? current-round))
      (do
        (logger/log :info :bright-cyan "Appending tiebreaker question: " tiebreaker-index)
        (assoc world :current-round (assoc current-round :questions (conj (:questions current-round) tiebreaker-index))
                      :tiebreaker-pool (subvec (:tiebreaker-pool world) 1)
                      :questions-repo (assoc questions-repo tiebreaker-index (update (get questions-repo tiebreaker-index) :text #(str "Tiebreaker: " %)))))
      world)))


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
    (w-m-d world (Event. :update-scores (:current-round new-world)))
    (w-m-d world (Event. :show-question {:text ""}))
    (add-tiebreaker-question-if-necessary new-world)))

(defn start-question
  [world event from-state to-state]
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
      (>!! game-channel (Event. :out-of-questions {:question-index question-index})))
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
    (w-m-d world (Event. :show-question {:text (str "Starting Round " (inc new-round-number) "...")}))
    (w-m-d world (Event. :update-scores {:scores [0 0 0 0] :questionnum 0}))
    (assoc world
           :round-index new-round-number
           :current-round (assoc new-round :question-index 0))
    ))



;
; TODO: This is really messy and could use some (serious) cleanup.
;
(defn game-loop
  [game-state world]
  (let [stage (:stage world)
        round-events (async/merge [(:buttons stage) (:quizmaster stage) game-channel])
        game (fsm/fsm-inc [
                           [:start {}
                            {:kind :start-round} -> {:action round-setup} :wait-for-question]
                           [:wait-for-question {}
                            {:kind :start-question} -> {:action start-question} question-on-quizmaster]
                           [question-on-quizmaster {}
                            {:kind :out-of-questions} -> end-of-round
                            {:kind :show-question} -> {:action buzz-timer} :wait-buzz]
                           [:wait-buzz {}
                            {:kind :show-question} -> {:action show-question} :wait-buzz
                            {:kind :buzz-timeout} ->  :wait-before-options
                            {:kind :buzz-pressed} -> {:action acc-answer} right-or-wrong]
                           [right-or-wrong {}
                            {:kind :select-right} -> {:action qm-choice} show-question-results
                            {:kind :select-wrong} -> {:action qm-choice} :wait-before-options]
                           [:wait-before-options {}
                            {:kind :start-choice} -> {:action options-show-and-timeout} wait-answers]
                           [wait-answers {}
                            {:kind :option-pressed} -> {:action accumulate-options} wait-answers
                            {:kind :options-timeout} -> show-question-results
                            {:kind :all-pressed} -> show-question-results]
                           [show-question-results {}
                            {:kind :start-question} -> {:action prepare-for-next-question} :wait-for-question]
                           [end-of-round {}
                            {:kind :start-round} -> {:action prepare-for-next-round} :start]
                           ])
        ]
    (loop [f (game (:state @game-state)
                   (merge world (:value @game-state) {:past-rounds [] :answers []}))]
      (let [initial-state (:state @game-state)]
        (reset! game-state f)
        (if (not (= initial-state (:state @game-state)))
          (logger/log :info :bright-green "Game state advancing: " (name (:state @game-state))))
      )

      (logger/info "State (top of game loop): " (name (:state @game-state)))

      ; Dump important bits from the game state into the console...
      (let [current-answer (get-in @game-state [:value :current-answer])
            current-round (get-in @game-state [:value :current-round])]
        (if (not (nil? (get current-round :number)))
          (logger/info "Round #" (get current-round :number)))

        (if (and (not (nil? current-answer))
                 (not (contains? #{:wait-for-question :end-of-round} (:state @game-state))))
          (do
            (logger/info "Question #" (+ (get current-round :question-index) 1) "/" (count (get current-round :questions))
                         " [+" (get-in current-answer [:question :score]) "]: "
                         (get-in current-answer [:question :text]))
            (logger/info "Answer: " (first (get-in current-answer [:question :options])))
            (logger/info "Choices: " (join " :: " (mapv #(% :text)
                                                    (get-in current-answer [:question :shuffled-options]))))

            (if (not (nil? (get current-answer :team-buzzed)))
              (let [color (if (= (:state @game-state) :right-or-wrong) :bright-white :default)]
                (logger/log :info color "Team #" (+ (get current-answer :team-buzzed) 1)
                                        " BUZZED in this question: " (case (get current-answer :good-buzz)
                                                                       nil "THINKING"
                                                                       true "CORRECT"
                                                                       false "WRONG"))))

            (if (not (nil? (get current-answer :answers)))
              (let [color (if (= (:state @game-state) :wait-answers) :bright-white :default)]
                (logger/log :info color "Team answers: [" (join " " (mapv (fn [answer] (if (nil? answer) "-" (+ answer 1)))
                                                                          (get current-answer :answers))) "]")))

            (let [color (if (= (:state @game-state) :show-question-results) :bright-white :default)]
              (logger/log :info color "Scores (question): [" (join " " (get current-answer :scores)) "]")))))

      ;; Patch the scores (see "omg-*" helper functions)...
      (let [scores [:value :current-round :scores]]
        (if (not= @score-adjustments [0 0 0 0])
          (do
            (logger/warn "Quizmaster adjusted round scores by " @score-adjustments ".")
            (reset! game-state (assoc-in @game-state scores
                                (mapv + (get-in @game-state scores) @score-adjustments)))
            (reset! score-adjustments [0 0 0 0])))

        (if (not (nil? (get-in @game-state scores)))
          (let [color (if (= (:state @game-state) :wait-for-question) :bright-white :default)]
            (logger/log :info color "Scores (round): [" (join " " (get-in @game-state scores)) "]"))))

      ;; Patch the questions list (see "omg-*" helper functions)...
      (let [questions [:value :current-round :questions]
            tiebreaker-pool [:value :tiebreaker-pool]
            round-number [:value :current-round :number]]
        (if (and @append-question (> (count (get-in @game-state tiebreaker-pool)) 0))
          (do
            (logger/warn "Quizmaster appended question " (first (get-in @game-state tiebreaker-pool)) " to round.")
            (reset! game-state (assoc-in @game-state questions
                                (conj (get-in @game-state questions) (first (get-in @game-state tiebreaker-pool)))))
            (reset! game-state (assoc-in @game-state tiebreaker-pool (subvec (get-in @game-state tiebreaker-pool) 1)))
            (reset! append-question false)))

        (if (not (nil? (get-in @game-state round-number)))
          (do
            (logger/info "Round (" (count (get-in @game-state questions)) " questions):"
                         " [" (join " " (get-in @game-state questions)) "]")
            (logger/info "Tiebreakers (" (count (get-in @game-state tiebreaker-pool)) " questions):"
                         " [" (join " " (get-in @game-state tiebreaker-pool)) "]"))))

      (logger/info "State (bottom of game loop): " (name (:state @game-state)))
      (recur (fsm/fsm-event @game-state (<!! round-events))))))


(defn read-from-file
  [what]
  (case what
    :items (merge (read-string (slurp config-file))
                  {:questions-repo (read-string (slurp questions-db))})
    :initial-state (let [saved (try
                                 (read-string (slurp game-state-file))
                                 (catch Exception e {:state :start :value {:round-index -1}}))
                         state-fn (ns-resolve *ns* (symbol (name (:state saved))))]
                     (logger/log :info :bright-green "Game state (initial): " (name (:state saved)))
                     (if (ifn? state-fn)
                       (assoc saved :state (deref state-fn))
                       saved))))


(def game-state (atom (read-from-file :initial-state)))

(defn save-game-state-to-file!
  [key ref old-state state]
  (let [value (:value state)
        current-state (:state state)
        full-state (pr-str {:state current-state
                            :value (select-keys value [:current-question
                                                       :current-answer
                                                       :current-round
                                                       :round-index
                                                       :tiebreaker-pool])})]
    ; If the current state is a function, is means the engine was stopped in the middle of a function-state and
    ; has just been restarted. If we saved the state now the engine could not be restarted again because function
    ; states cannot be used as initial state. This is not a problem, it will save on the next state transition.
    (if (starts-with? (name current-state) "fn-")
      (logger/warn "Probably recovering from a crash. Logging state but NOT saving it.")
      (spit game-state-file full-state :append false))
    (spit (str game-state-file ".log") (str full-state "\n") :append true)))  ; ...record *all* states.

(defn -main
  [& args]
  (let [game-items (read-from-file :items)
        stage (setup-stage)
        world (assoc game-items :stage stage)
        current-state (:state @game-state)]
    (add-watch game-state nil save-game-state-to-file!)
    (if (= current-state :wait-buzz)
      (do
        (logger/warn "Restarting buzz timer...")
        (buzz-timer world)))
    (if (= current-state (deref (ns-resolve *ns* (symbol (name :wait-answers)))))
      (do
        (logger/warn "Restarting options timer...")
        (options-timer world)))
    (game-loop game-state world)))


;; Start a debug REPL, to which you can connect with "lein repl :connect"...
(defonce server (repl/start-server :port 7888))

;; Run this upon connecting (would be nice if it ran automatically):
;; (require '[pixelsquiz.core :refer :all])

(defn omg-mainscreen
  ([question]
    (omg-mainscreen question "" "" "" ""))
  ([question o1 o2 o3 o4]
    (let [world (:value @game-state)]
      (w-m-d world {:kind :show-options
                    :bag-of-props {:question {:text question
                                              :shuffled-options (mapv #(assoc {} :text %) [o1 o2 o3 o4])}}}))))

(defn omg-teams [t1 t2 t3 t4]
  (omg-mainscreen "These are the teams:" t1 t2 t3 t4))

(defn omg-last-question-scores []
  (get-in @game-state [:value :current-answer :scores]))

; TODO: There must be a cleaner way to trigger the main loop... :/
(defn omg-apply-now []
  (http/post "http://127.0.0.1:3000/actions/apply-omg")
  true)

(defn omg-adjust-scores [t1 t2 t3 t4]
  (reset! score-adjustments [t1 t2 t3 t4])
  (logger/warn "OMG: Scores adjusted by " @score-adjustments ". Game screen will update when question ends.")
  (omg-apply-now))

(defn omg-revert-scores []
  (apply omg-adjust-scores (mapv - [0 0 0 0] (omg-last-question-scores))))

(defn omg-append-question []
  (reset! append-question true)
  (logger/warn "OMG: Question " (first (get-in @game-state [:value :tiebreaker-pool])) " appended to current round.")
  (omg-apply-now))

(defn omg-replace-question []
  (omg-revert-scores)
  (omg-append-question))

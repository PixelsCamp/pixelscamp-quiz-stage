(ns pixelsquiz.core
  (:gen-class))

(require '[clojure.spec :as s])
(require '[reduce-fsm :as fsm])
(require '[clojure.core.async :as async
           :refer [>! <! >!! <!! go chan buffer close! thread
                    alts! alts!! timeout]])
(require '[clojure.edn :as edn])

(defrecord Event [kind bag-of-props])
(defrecord Team [playerA playerB])
(defrecord Question [id kind score text options])
(defrecord Answer [question buzzed answers scores])
(defrecord Round [number teams questions])
(defrecord GameState [round-index current-round question-index current-question])

(defn buzz-timer 
  [c & _]
  (go
    (<! (timeout 20000))
    (>! c (Event. :buzz-timeout {})))
  )


(defn option-timeout 
  [c & _]
  (go
    (<! (timeout 20000))
    (>! c (Event. :option-timeout {})))
  )

(defn show-question
  [acc event from-state to-state]
  answer
  )

(defn show-question
  [answer ])

(defmacro e-t
  [t]
  `{:kind ~t)

(defn question-fsm 
  [question events]
  (let [timeout-chan (chan)
        question-fsm (fsm/fsm-inc [
                           [:start {}
                            (e-t :start-timer) -> {:action (partial buzz-timer timeout-chan) } :pre-question
                            (e-t :show-question) -> {:action show-question} :wait-buzz]
                           [:wait-buzz {}
                            (e-t :buzz-timeout) -> :wait-options
                            (e-t :buzz-pressed) -> :right-or-wrong]
                           [:pre-question {}
                            (e-t :show-question) -> :wait-buzz]
                           [:right-or-wrong {}
                            (e-t :select-right) -> :show-results
                            (e-t :select-wrong) -> :wait-options]
                           [:wait-options {}
                            (e-t :option-pressed) -> :wait-options
                            (e-t :option-timeout) -> :show-results
                            (e-t :all-pressed) -> :show-results]
                           [:show-results {:is-terminal true}
                            _ -> :show-results]
                           ])]
    (loop [f (question-fsm
               :start ; the initial state
               {:question question
                :answer (Answer. [false [nil nil nil nil] [0 0 0 0]])} ; the state of the accumulator
              )
           e (async/merge events timeout-chan)]
      (case (:is-terminated? f)
        true (:value f)
        (recur (fsm/fsm-event f (<!! e)) e))
      )))


(defn run-question 
  [world event from-state to-state]
  (let [events (async/merge (:buttons world) (:quizmaster world))
        question-chan (:question-chan world)
        question-index (+ 1 (:question-index world))
        current-round (:current-round world)
        question-number (get (:questions current-round) question-index)
        question (get (:questions world) question-number)]
      (case question
        nil (>!! question-chan (Event. :out-of-questions {:question-index question-index}))
        (>!! question-chan (Event. :question-ended {:result (question-fsm question events) }))
        )))


(defn to-round-setup 
  [acc event from-state to-state]
  (let [new-round-number (+ 1 (:round acc))
        current-round (nth (:rounds acc) new-round-number)]
    (assoc acc 
           :round-index new-round-number
           :current-round (get (:rounds acc) new-round-number))))

(defn game-loop
  [stage rounds questions game-state]
  (let [question-chan (chan)
        game (fsm/fsm-inc [
                           [:start
                            (e-t :start-round) -> {:action to-round-setup} :round-setup]
                           [:round-setup
                            (e-t :start-question) -> {:action (partial run-question :on-question]
                           [:on-question
                            (e-t :question-ended) -> :wait-continue
                            (e-t :out-of-questions) -> :end-of-round]
                           [:wait-continue
                            (e-t :start-question) -> :on-question]
                           [:end-of-round
                            (e-t :start-round) -> :round-setp]
                           ])
          events (async/merge (:buttons stage) (:quizmaster stage) question-chan) ; XXX there's a bunch of knowledge buried here
          
        ]
    (loop [f (game :start (assoc @game-state 
                                 :stage stage
                                 :rounds rounds
                                 :questions questions
                                 :question-chan question-chan)
                                 ev (<!! e)]
      (println f)
      (reset! game-state (:value f))
      (recur (fsm/fsm-event f) e))
    )
  )

(defn buttons-actor
  []
  (chan))
(defn main-display-actor
  []
  (chan))
(defn quizmaster-actor
  []
  (chan))

(defn read-from-file
  [what]
  (case what
    :items {
            :rounds [
                     (Round. 1 ['a 'b 'c 'd] [1 2 3 4 5 6 7])
                     (Round. 2 ['e 'f 'g 'h] [1 10 11 12 13 14 15])
                     ]
            :questions [
                        (Question. 1 :multi 1 "The first question" 
                                   [
                                    "First answer"
                                    "Second answer"
                                    "Third answer"
                                    "Last answer"
                                  ])
                        ]
            }
    :initial-state {:round 1}
    ))


(defn -main
  [& args]
  (let [
        game-items (read-from-file :items)
        stage {
               :buttons (buttons-actor)
               :main-display (main-display-actor)
               :player-lights (player-lights-actor)
               :quizmaster (quizmaster-actor)
               }
        game-state (atom (read-from-file :initial-state))
        ]
    (game-loop stage (:rounds game-items) (:questions game-items) game-state) 
   )
  )

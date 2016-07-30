(ns pixelsquiz.core
  (:gen-class))

(require '[clojure.spec :as s])
(require '[reduce-fsm :as fsm])
(require '[clojure.core.async :as async
           :refer [>! <! >!! <!! go chan buffer close! thread
                    alts! alts!! timeout]])

(defrecord Event [kind bag-of-props])
(defrecord Team [playerA playerB])
(defrecord Question [kind score text options])
(defrecord Answer [question buzzed answers scores])
(defrecord Round [teams questions scores])
(defrecord GameState [round ])

(defn show-question
  [answer event from-state to-state]
  answer
  )

(defn show-question
  [answer ])

(defn question-fsm 
  [stage events]
  (let [game (fsm/fsm-inc :start ; the initial state
                          (Answer. [false [nil nil nil nil] [0 0 0 0]]) ; the state of the accumulator
                          [
                           [:start
                            :start-timer -> :pre-question
                            :show-question -> :wait-buzz]
                           [:wait-buzz
                            :buzz-timeout -> :wait-options
                            :buzz-pressed -> :right-or-wrong]
                           [:pre-question
                            :show-question -> :wait-buzz]
                           [:wait-options
                            :option-pressed -> :wait-options
                            :option-timeout -> :show-results
                            :all-pressed -> :show-results]
                           [:right-or-wrong
                            :select-right -> :show-results
                            :select-wrong -> :show-results]
                           [:show-results {:is-terminal true}
                            _ -> :show-results]
                           ])]
    (loop [f (game) e events]
      (println f)
      (recur (fsm/fsm-event f (<!! e)) e)
      )
    ))


(defn run-question 
  [question player-buttons]
  (answer-from (question-fsm player-buttons))
  )


(defn game-fsm
  [initial-state events]
  (let [game (fsm/fsm-inc [
                           [:start
                            :start-round -> :round-setup]
                           [:round-setup
                            :start-question -> :on-question]
                           [:on-question
                            :question-ended -> :wait-continue
                            :out-of-questions -> :end-of-round]
                           [:wait-continue
                            :start-question -> :on-question]
                           ])]
    (loop [f (game) e events]
      (println f)
      (recur (fsm/fsm-event f (<!! e)) e))
    )
  )

(defn setup-buttons
  [])
(defn setup-lights
  [])
(defn setup-display
  [])

(defn from-persistence
  [])

(defn -main
  [& args]
  (let [player-buttons (setup-buttons)
        main-display (setup-display)
        player-lights (setup-lights)]
    (game-fsm (from-persistence)) 
   )
  )

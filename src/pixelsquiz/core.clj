(ns pixelsquiz.core
  (:gen-class))

(require '[pixelsquiz.stage :refer :all])
(require '[pixelsquiz.types])
(import '(pixelsquiz.types Event Answer Question Round Team GameState))

(require '[clojure.spec :as s])
(require '[reduce-fsm :as fsm])
(require '[clojure.core.async :as async
           :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                    alts! alts!! timeout]])
(require '[clojure.edn :as edn])

(require 'spyscope.core)

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
  [world event from-state to-state]
  (<!! (-> world :stage :main-display) (:question world))
  )


(defn question-fsm 
  [question events stage]
  (let [timeout-chan (chan)
        question-fsm (fsm/fsm-inc [
                           [:start {}
                            {:kind :start-timer} -> {:action (partial buzz-timer timeout-chan) } :pre-question
                            {:kind :show-question} -> {:action show-question} :wait-buzz]
                           [:wait-buzz {}
                            {:kind :buzz-timeout} -> :wait-options
                            {:kind :buzz-pressed} -> :right-or-wrong]
                           [:pre-question {}
                            {:kind :show-question} -> :wait-buzz]
                           [:right-or-wrong {}
                            {:kind :select-right} -> :show-results
                            {:kind :select-wrong} -> :wait-options]
                           [:wait-options {}
                            {:kind :option-pressed} -> :wait-options
                            {:kind :option-timeout} -> :show-results
                            {:kind :all-pressed} -> :show-results]
                           [:show-results {:is-terminal true}
                            _ -> :show-results]
                           ])]
    (loop [f (question-fsm
               :start ; the initial state
               {
                :question question
                :answer (Answer. question false [nil nil nil nil] [0 0 0 0])
                :stage stage
                } ; the state of the accumulator
              )
           e (async/merge [events timeout-chan])]
      (case (:is-terminated? f)
        true (:value f)
        (recur (fsm/fsm-event f (<!! e)) e))
      )))


(defn run-question 
  [world event from-state to-state]
  (let [events (async/merge [(:buttons world) (:quizmaster world)])
        question-chan (:question-chan world)
        question-index (+ 1 (:question-index world))
        current-round (:current-round world)
        question-number (get (:questions current-round) question-index)
        question (get (:questions world) question-number)]
      (case question
        nil (>!! question-chan (Event. :out-of-questions {:question-index question-index}))
        (>!! question-chan (Event. :question-ended {:result (question-fsm question events (:stage world)) }))
        )))


(defn to-round-setup 
  [acc event from-state to-state]
  (let [new-round-number (+ 1 (:round-index acc))
        current-round (get (:rounds acc) new-round-number)]
    (println (str "to-round-setup " new-round-number " " current-round))
    (assoc acc 
           :round-index new-round-number
           :current-round current-round
           :question-index -1)))

(defn game-loop
  [stage rounds questions game-state]
  (let [question-chan (chan)
        game (fsm/fsm-inc [
                           [:start {}
                            {:kind :start-round} -> {:action to-round-setup} :round-setup]
                           [:round-setup {}
                            {:kind :start-question} -> {:action run-question} :on-question]
                           [:on-question {}
                            {:kind :question-ended} -> :wait-continue
                            {:kind :out-of-questions} -> :end-of-round]
                           [:wait-continue {}
                            {:kind :start-question} -> :on-question]
                           [:end-of-round {}
                            {:kind :start-round} -> :round-setup]
                           ])
          events (async/merge [(:buttons stage) (:quizmaster stage) question-chan]) ; XXX there's a bunch of knowledge buried here
          
        ]
    (loop [f (game :start (assoc @game-state 
                                 :stage stage
                                 :rounds rounds
                                 :questions questions
                                 :question-chan question-chan))]
      (reset! game-state (:value f))
      (recur (fsm/fsm-event f (<!! events))))
  ))


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
    :initial-state {:round-index -1}
    ))


(defn -main
  [& args]
  (let [
        game-items (read-from-file :items)
        stage (setup-stage) 
        game-state (atom (read-from-file :initial-state))
        ]
    (game-loop stage (:rounds game-items) (:questions game-items) game-state) 
   )
  )

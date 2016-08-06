(ns pixelsquiz.core
  (:gen-class))


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
  [m]
  `(>!! (-> ~'world  :stage :main-display) ~m))

(defn show-question
  [world & _]
  (w-m-d (-> world :question :text))
  world)


(defn buzz-timedout
  [world & _]
  (w-m-d "timeout!!" )
  world)

(defn wake-up-quizmaster
  [world event from-state to-state]
  (w-m-d "waiting for quizmaster right wrong")
  world)

(defn show-question-results
  [world & _]
  (w-m-d (:current-answer world))
  false)

(defmacro with-answer 
  [& forms]
  `(let [{question :question buzzed :buzzed answers :answers scores :scores} (:current-answer ~'world)]
     ~@forms))

(defn acc-answer
  [world event & _]
  (with-answer
   (assoc world :current-answer (Answer. question buzzed (assoc answers (-> event :bag-of-props :remote) (-> event :bag-of-props :button)) scores))))



(defn options-show-and-timeout
  [c world & _]
  (w-m-d (-> world :question :options))
  (options-timeout c world)
  world)

(defn fsm-fn
  [world event from-state to-state]
  
  world)


(defn prepare-question 
  [answer-chan world event from-state to-state]
  (let [events (chan)
        question-index (+ 1 (:question-index world))
        current-round (:current-round world)
        question-number (get (:questions current-round) question-index)
        question (get (:questions world) question-number)]
      (case question
        nil (>!! answer-chan (Event. :out-of-questions {:question-index question-index}))
        :nothing)
    (assoc world 
            :question-index question-index
            :question question
            :current-answer (Answer. question false [nil nil nil nil] [0 0 0 0])
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
  (let [timeout-chan (chan 16)
        round-events (async/merge [(:buttons stage) (:quizmaster stage) timeout-chan])
        game (fsm/fsm-inc [
                           [:start {}
                            {:kind :start-round} -> {:action to-round-setup} :round-setup]
                           [:round-setup {}
                            {:kind :start-question} -> {:action (partial prepare-question timeout-chan)} :start-question]
                           [:start-question {}
                            {:kind :out-of-questions} -> :end-of-round
                            {:kind :show-question} -> {:action (partial buzz-timer timeout-chan)} :wait-buzz]
                           [:wait-buzz {}
                            {:kind :show-question} -> {:action show-question} :wait-buzz
                            {:kind :buzz-timeout} -> {:action buzz-timedout} :wait-before-options
                            {:kind :buzz-pressed} -> {:action wake-up-quizmaster} :right-or-wrong]
                           [:right-or-wrong {}
                            {:kind :select-right} -> {:action acc-answer} show-question-results
                            {:kind :select-wrong} -> {:action acc-answer} :wait-before-options]
                           [:wait-before-options {}
                            {:kind :start-mult} -> {:action (partial options-show-and-timeout timeout-chan)} :wait-answers]
                           [:wait-answers {}
                            {:kind :option-pressed} -> {:action acc-answer} :wait-answers
                            {:kind :options-timeout} -> show-question-results
                            {:kind :all-pressed} -> show-question-results]
                           [show-question-results {}
                            {:kind :next-question} -> :round-setup]
                           [:end-of-round {}
                            {:kind :next-round} -> :start]
                           ])
        ]
    (loop [f (game (:state @game-state) 
                   (assoc (:value @game-state) 
                                 :stage stage
                                 :rounds rounds
                                 :questions questions))]
      (reset! game-state f)
      (recur (fsm/fsm-event f #spy/d (<!! round-events))))
  ))


(defn read-from-file
  [what]
  (case what
    :items {
            :rounds [
                     (Round. 1 ['a 'b 'c 'd] [1 2 3 4 5 6 7])
                     (Round. 2 ['e 'f 'g 'h] [1 10 11 12 13 14 15])
                     ]
            :questions [ nil
                        (Question. 1 :multi 1 "The first question" 
                                   [
                                    "First answer"
                                    "Second answer"
                                    "Third answer"
                                    "Last answer"
                                  ])
                        ]
            }
    :initial-state {:state :start :value {:round-index -1}}
    ))


(def game-state (atom (read-from-file :initial-state) ))
(defonce server (repl/start-server :port 7888))

(defn -main
  [& args]
  (let [
        game-items (read-from-file :items)
        stage (setup-stage) 
        ]
    (add-watch game-state nil (fn [k r os s] (println (str "main loop " s))))
    (game-loop stage (:rounds game-items) (:questions game-items) game-state) 
   )
  )

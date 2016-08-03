(ns pixelsquiz.stage
    (:gen-class))

(require '[pixelsquiz.types])
(import pixelsquiz.types.Event)

(require '[compojure.api.sweet :refer :all])
(require '[ring.util.http-response :refer :all])

(require '[clojure.core.async :as async
           :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                    alts! alts!! timeout]])

(defn buttons-actor
  []
  (let [c (chan)]
    {:actor :buttons
     :chan c
     :routes nil}))

(defn main-display-actor
  []
  (let [main-display-channel (chan)]
    (go-loop [ev {:kind :starting}]
            (println "main-display:" ev)                 
            (recur (<! main-display-channel)))
    {:actor :main-display
     :chan main-display-channel
     :routes nil}))

(defn player-lights-actor
  []
  (let [player-lights-channel (chan)]
    (go-loop [ev {:kind :starting}]
            (println "main-display:" ev)                 
            (recur (<! player-lights-channel)))
    {:actor :player-lights
     :chan player-lights-channel
     :routes nil}))

(defn quizmaster-actor
  []
  (let [quizmaster-channel (chan)]
      {:actor :quizmaster
       :chan quizmaster-channel
       :routes (POST "/start-round" [] 
                     ((>!! quizmaster-channel (Event. :start-round {}))
                      (ok {:message (str "Hello, " name)}))
                     )}
      ))

(defn setup-stage
  []
  (let [actors [(buttons-actor) (main-display-actor) (player-lights-actor) (quizmaster-actor)]
        ui-routes (map #(:routes %) actors)
        web-api (api ui-routes)]
      (apply merge (map #(assoc {} (:actor %) (:chan %)) actors))
    ))

(ns pixelsquiz.stage

  (:use org.httpkit.server)

  (:require
    [pixelsquiz.types :refer :all]
    [pixelsquiz.sounds :as sounds]
    [pixelsquiz.buzz :as buzz :refer [open-and-read-buzz-into]]
    [ring.middleware.defaults]
    [compojure.core     :as comp :refer (defroutes GET POST)]
    [compojure.route    :as route]
    [clojure.core.async :as async :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                                          alts! alts!! timeout]]
    [cheshire.core :as json]
    )

  )

(import pixelsquiz.types.Event)

(require 'spyscope.core)
(require '[alex-and-georges.debug-repl :refer :all])





(defn buttons-actor
  []
  (let [c (chan 16)]
    (go (open-and-read-buzz-into c))
    {:actor :buttons
     :chan c
     :routes nil}
  ))

(defn main-display-actor
  []
  (let [ws-connections (atom {})
        main-display-channel (chan 16)]
    (go-loop [ev {:kind :starting}]
             (println (str "      main-display: " ev))
             (doseq [client (keys @ws-connections)]
               ;; send all, client will filter them
               (send! client (json/generate-string ev)))
             (recur (<! main-display-channel)))
    {:actor :main-display
     :chan main-display-channel
     :routes (GET "/maindisplay" req 
                  (with-channel req channel              ; get the channel
                    ;; communicate with client using method defined above
                    (on-close channel (fn [status]
                                        (swap! ws-connections dissoc channel)
                                        (println "channel closed")))
                    (if (websocket? channel)
                      (do (println "channel conn") (swap! ws-connections assoc channel true))
                      (println "HTTP channel"))
                    (on-receive channel (fn [data]       
                                          ; data received from client
                                          ;; An optional param can pass to send!: close-after-send?
                                          ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
                                          ;; and false for WebSocket.  (send! channel data close-after-send?)
                                          (send! channel data)))) ; data is sent directly to the client 
                  )
     }))

(defn player-lights-actor
  []
  (let [ws-connections (atom {}) 
        player-lights-channel (chan 16)]
    (go-loop [ev {:kind :starting}]
             (println "        player-lights: " ev)                 
             (recur (<! player-lights-channel)))
    {:actor :player-lights
     :chan player-lights-channel
     :routes nil}))

(defn quizmaster-actor
  []
  (let [quizmaster-channel (chan 16)]
    {:actor :quizmaster
     :chan quizmaster-channel
     :routes (POST "/actions/:action" [action] 
                   (>!! quizmaster-channel (Event. (keyword action) {}))
                   (str "ok " (keyword action) "\n"))
     }
    ))

(defn setup-stage
  []
  (let [
        actors [(buttons-actor) (main-display-actor) (player-lights-actor) (quizmaster-actor)]
        ui-routes [(:routes (nth actors 3))
                   (:routes (nth actors 1))]
        ]
    (run-server (apply comp/routes ui-routes) {:port 3000})
    (apply merge (map #(assoc {} (:actor %) (:chan %)) actors))
    ))

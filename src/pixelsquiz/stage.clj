(ns pixelsquiz.stage

  (:use org.httpkit.server)

  (:require
    [pixelsquiz.types]
    [pixelsquiz.sounds :as sounds]
    [ring.middleware.defaults]
    [compojure.core     :as comp :refer (defroutes GET POST)]
    [compojure.route    :as route]
    [clojure.core.async :as async :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                                          alts! alts!! timeout]]
    [clj-time.core :as t]
    [clj-time.coerce :as tc]
    [cheshire.core :as json]
    )

  (:import [com.codeminders.hidapi HIDDeviceInfo HIDManager]
           [java.io IOException])
  )

(import pixelsquiz.types.Event)

(require 'spyscope.core)
(require '[alex-and-georges.debug-repl :refer :all])

(def controller-buttons [:red :yellow :green :orange :blue])



(defn load-hid-natives []
  (let [bits (System/getProperty "sun.arch.data.model")]
    (clojure.lang.RT/loadLibrary (str "hidapi-jni-" bits))))


(defn open-buzz []
  (try
    (let [_ (load-hid-natives)
          manager (HIDManager/getInstance)]
      (.openById manager 1356 2 ""))
    (catch Exception e nil))
  )

(defn debounce-buttons
  [current previous]
  (bit-and current (bit-xor current previous))
  )


(defn buzz-to-properties
  [buttons team]
  (map #(assoc {}
          :button (get controller-buttons %) 
          :button-index %
          :pressed (> (bit-and buttons (bit-shift-left 0x1 %)) 0)
          :team team
          ) (range (count controller-buttons))))

(defn read-buzz [dev channel]
  (try
    (let [buf (byte-array 5)
          ]
      (loop [br 0
             previous [0 0 0 0]] 
        (let [states (if (= br 5) 
                       (let [ts (tc/to-long (t/now))
                             b1 (aget buf 2)
                             b2 (aget buf 3)
                             b3 (aget buf 4)
                             states [
                                     ;; A b1 0-4
                                     ;; B b1 5-7 b2 0-1
                                     ;; C b2 2-6
                                     ;; D b2 7 b3 0-3
                                     (bit-and 0x1f b1)
                                     (bit-and 0x1f (bit-or (bit-shift-left b2 3) (unsigned-bit-shift-right b1 5)))
                                     (bit-and 0x1f (unsigned-bit-shift-right b2 2))
                                     (bit-and 0x1f (bit-or (bit-shift-left b3 1) (bit-and 0x1 (unsigned-bit-shift-right b2 7))))
                                     ]
                             ]
                         (doseq [props (flatten (map buzz-to-properties (map debounce-buttons states previous) (range 4)))
                                 :when (:pressed props)
                                 ]
                            (>!! channel (Event. (case (:button props)
                                                  :red :buzz-pressed
                                                  :option-pressed) props))
                           )
                         states)
                       previous)
              ] 
          (recur (.readTimeout dev buf -1) states)))
      )
    (catch Exception e nil))
  )

(defn open-and-read-buzz-into [channel]
  (loop [dev (open-buzz)]
    (if (nil? dev)
      (do 
        (Thread/sleep 1000)
        (recur (open-buzz)))
      (do
        (read-buzz dev channel)
        (recur (open-buzz)))
      )))

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

(ns pixelsquiz.stage
  (:gen-class)

  (:require
    [pixelsquiz.types]
    [ring.middleware.defaults]
    [compojure.core     :as comp :refer (defroutes GET POST)]
    [compojure.route    :as route]
    [taoensso.encore    :as encore :refer (have have?)]
    [taoensso.timbre    :as timbre :refer (tracef debugf infof warnf errorf)]
    [taoensso.sente     :as sente]
    [org.httpkit.server :as http-kit]
    [taoensso.sente.server-adapters.http-kit :refer (get-sch-adapter)]
    [clojure.core.async :as async :refer [>! <! >!! <!! go go-loop chan buffer close! thread
             alts! alts!! timeout]])
     )

(import pixelsquiz.types.Event)





(defn buttons-actor
  []
  (let [c (chan 16)]
    {:actor :buttons
     :chan c
     :routes nil}))

(defn main-display-actor
  []
  (let [main-display-channel (chan 16)]
    (go-loop [ev {:kind :starting}]
            (println (str "      main-display: " ev))                 
            (recur (<! main-display-channel)))
    {:actor :main-display
     :chan main-display-channel
     :routes nil}))

(defn player-lights-actor
  []
  (let [player-lights-channel (chan 16)]
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
  (let [actors [(buttons-actor) (main-display-actor) (player-lights-actor) (quizmaster-actor)]
;        ui-routes (map #(:routes %) actors)]
      ui-routes (:routes (nth actors 3))]
    (http-kit/run-server (comp/routes ui-routes) {:port 3000})
      (apply merge (map #(assoc {} (:actor %) (:chan %)) actors))
    ))

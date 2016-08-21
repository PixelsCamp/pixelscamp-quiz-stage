(ns pixelsquiz.stage

  (:use org.httpkit.server)

  (:require
    [pixelsquiz.types :refer :all]
    [pixelsquiz.util :refer :all]
    [pixelsquiz.sounds :as sounds]
    [pixelsquiz.buzz :as buzz :refer [open-and-read-buzz-into]]
    [pixelsquiz.logger :as logger]
    [ring.middleware.defaults :refer :all]
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
     :routes (POST "/buttons/:action" [action :as request]
                   (>!! c (Event. (keyword action) (assoc (:params request)
                                                          :team (read-string (-> request :params :team))
                                                          :button-index (read-string (-> request :params :button-index)))))
                   (str "ok " (keyword action) "\n"))
     }
  ))

(defn play-sounds-for!
  [ev]
  (case (:kind ev)
    :timer-start (sounds/play :ping)
    :buzzed (sounds/play (get [:t1 :t2 :t3 :t4] (-> ev :bag-of-props :team-buzzed)))
    :default
    )
  )


(defn format-for-displays
  [ev]
  (try 
    (case (:kind ev)
      :timer-update {:do :timer-update :value (-> ev :bag-of-props :value)}
      :buzzed {:do :highlight :team (-> ev :bag-of-props :team-buzzed)}
      :update-lights {:do :update-lights :colours (-> ev :bag-of-props :current-answer :answers) }
      :show-question {:do :show-question 
                      :text (-> ev :bag-of-props :text) 
                      :options ["" "" "" ""]
                      }
      :show-options {:do :show-question ; ev bag-of-props Answer
                     :text (-> ev :bag-of-props :question :text)
                     :options (map #(:text %) (-> ev :bag-of-props :question :shuffled-options))
                     }
      :show-question-results {:do :update-lights  ; ev bag-of-props Answer
                              :scores (-> ev :bag-of-props :scores)
                              :options 
                              (:s (reduce (fn [acc o] 
                                                     {:t (inc (:t acc)) 
                                                      :s (if (nil? o)
                                                           (:s acc)
                                                           (assoc (:s acc) o (str (get (:s acc) o) " " (inc (:t acc)))))
                                                      }) {:t 0 :s (mapv #(str (:text %) " - ") (-> ev :bag-of-props :question :shuffled-options))} (-> ev :bag-of-props :answers)
                                                   ))
                              }
      :update-scores {:do :update-scores :scores (-> ev :bag-of-props :scores) :questionnum (-> ev :bag-of-props :question-index) } ; Round
      :end-of-round {:do :update-all ; ev bag-of-props Round
                     :text "Round ended!"
                     :options (map #(str "Team " (:team %) " - " (:score %) " points") 
                                   (sort-teams-by-scores (-> ev :bag-of-props :scores)))
                     :scores (-> ev :bag-of-props :scores)
                     :questionnum (-> ev :bag-of-props :question-index)
                     }
      (logger/error "format-for-displays " ev))
    (catch Exception e (logger/error "exception in format-for-displays" ev e))))

(defn format-for-quizmaster
  [ev]
  (try
    (case (:kind ev)
      :for-quizmaster (merge {:do :quizmaster-only} (:bag-of-props ev))
      :buzzed (assoc {:do :quizmaster-only} :getrightwrong (-> ev :bag-of-props :team-buzzed))
      nil)
    (catch Exception e (logger/error "exception in format-for-quizmaster" ev e))
    ))

(defn displays-actor
  []
  (let [ws-connections (atom {})
        qm-connections (atom {})
        displays-channel (chan 16)]
    (go-loop [ev {:kind :starting}]
             (let [message (format-for-displays ev)
                   qm-mesg (format-for-quizmaster ev)]
               (play-sounds-for! ev)
               (if (not (nil? message))
                 (doseq [client (keys @ws-connections)]
                   ;; send all, client will filter them
                   (send! client (json/generate-string message))))
               (if (not (nil? qm-mesg))
                 (doseq [client (keys @qm-connections)]
                   (send! client (json/generate-string qm-mesg)))))
             (recur (<! displays-channel)))
    {:actor :displays
     :chan displays-channel
     :routes (GET "/displays" req 
                  (with-channel req channel              ; get the channel
                    ;; communicate with client using method defined above
                    (on-close channel (fn [status]
                                        (swap! ws-connections dissoc channel)
                                        (swap! qm-connections dissoc channel)
                                        (println "channel closed")))
                    (if (websocket? channel)
                      (do (println "channel conn") (swap! ws-connections assoc channel true))
                      (println "HTTP channel"))
                    (on-receive channel (fn [data]       
                                          ; data received from client
                                          ;; An optional param can pass to send!: close-after-send?
                                          ;; When unspecified, `close-after-send?` defaults to true for HTTP channels
                                          ;; and false for WebSocket.  (send! channel data close-after-send?)
                                          (let [message (json/parse-string data true)]
                                            (case (:kind message)
                                                  "quizmaster-auth" (do (swap! qm-connections assoc channel true)
                                                                       (send! channel (json/generate-string {:kind :info
                                                                                                             :text "OK!"})))
                                                  (logger/warn "received data from displays:" data)))))))
     }))


(defn quizmaster-actor
  []
  (let [quizmaster-channel (chan 16)]
    {:actor :quizmaster
     :chan quizmaster-channel
     :routes (POST "/actions/:action" [action :as request]
                   (>!! quizmaster-channel (Event. (keyword action) (:params request)))
                   (str "ok " (keyword action) "\n"))
     }
    ))

(defn setup-stage
  []
  (let [
        actors [(displays-actor) (quizmaster-actor) (buttons-actor)]
        ui-routes [(:routes (nth actors 0))
                   (:routes (nth actors 1))
                   (:routes (nth actors 2)) ; XXX humm ...
                   (route/files "/static/" {:root "html/pixelsquiz/"})]
        ]
    (run-server (wrap-defaults (apply comp/routes ui-routes) api-defaults) {:port 3000})
    (apply merge (map #(assoc {} (:actor %) (:chan %)) actors))
    ))

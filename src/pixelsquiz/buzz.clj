(ns pixelsquiz.buzz
  (:require
    [pixelsquiz.types :refer :all]
    [clojure.core.async :as async :refer [>! <! >!! <!! go go-loop chan buffer close! thread
                                          alts! alts!! timeout]]
    [clj-time.core :as t]
    [clj-time.coerce :as tc]
    )

  (:import [com.codeminders.hidapi HIDDeviceInfo HIDManager]
           [java.io IOException])
  )
(import pixelsquiz.types.Event)

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


(def button-mapping [nil 3 2 1 0])

(defn buzz-to-properties
  [buttons team]
  (map #(assoc {}
          :button (get controller-buttons %) 
          :button-index (get button-mapping %)
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

(ns pixelsquiz.logger
  (:require
    [clj-time.core :as t]
    [clj-time.coerce :as tc]
    )
  )

(defn print-with-serverity
  [severity args]
  (println severity  (.toString (t/now))  args)
  true)

(defn info
  [& args]
  (print-with-serverity "INFO" args )
  )

(defn error
  [& args]
  (print-with-serverity "ERROR" args)
  nil)

(defn warn
  [& args]
  (print-with-serverity "WARN" args))


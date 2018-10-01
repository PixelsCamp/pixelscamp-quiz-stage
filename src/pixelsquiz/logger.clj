(ns pixelsquiz.logger
  (:require
    [clj-time.local :refer [local-now format-local-time]]
    [clojure.string :refer [join]]
   ))

(defn print-with-severity
  [severity args]
  (let [timestamp (format-local-time (local-now) :rfc822)]
    (println (join ": " [timestamp severity (join "" args)]))))

(defn info
  [& args]
  (print-with-severity "INFO" args))

(defn error
  [& args]
  (print-with-severity "ERROR" args))

(defn warn
  [& args]
  (print-with-severity "WARNING" args))

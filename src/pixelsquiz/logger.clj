(ns pixelsquiz.logger
  (:require
    [simple-time.core :as time]
    [clojure.string :refer [join upper-case]]))


(def colors {:reset   "\033[0m"
             :default "\033[39m"
             :black   "\033[30m"
             :white   "\033[37m"  :bright-white   "\033[37;1m"
             :red     "\033[31m"  :bright-red     "\033[31;1m"
             :green   "\033[32m"  :bright-green   "\033[32;1m"
             :blue    "\033[34m"  :bright-blue    "\033[34;1m"
             :yellow  "\033[33m"  :bright-yellow  "\033[33;1m"
             :magenta "\033[35m"  :bright-magenta "\033[35;1m"
             :cyan    "\033[36m"  :bright-cyan    "\033[36;1m"})

(defn colorize
  [color text]
  (join "" [(color colors) text (:reset colors)]))

(defn log
  [severity color arg & args]
  (let [timestamp (time/format (time/now) "yyyy-MM-dd HH:mm:ss.SSS")
        severity (upper-case (name severity))
        text (join "" (concat arg args))]
    (println (join ": " [timestamp severity (colorize color text)]))))

(defn info
  [& args]
  (log :info :default args))

(defn error
  [& args]
  (log :error :bright-red args))

(defn warn
  [& args]
  (log :warning :bright-yellow args))

(defproject pixelsquiz "0.2.0"
  :description "Game engine for the Pixels Camp quiz"
  :url "https://github.com/brpx/pixelscamp-quiz-stage"
  :dependencies [
    [org.clojure/clojure "1.8.0"]
    [org.clojure/core.async "0.3.465"]
    [org.clojure/tools.nrepl "0.2.13"]
    [org.clojure/tools.namespace "0.2.11"]
    [spyscope "0.1.5"]
    [org.clojars.gjahad/debug-repl "0.3.3"]
    [reduce-fsm "0.1.4"]
    [http-kit "2.2.0"] ; Default
    [ring "1.5.1"]
    [ring/ring-defaults "0.3.1"] ; Includes `ring-anti-forgery`, etc.
    [compojure "1.5.2"] ; Or routing lib of your choice
    [org.clojars.torbjornvatn/hidapi "1.1"]
    [clj-time "0.12.2"]
    [cheshire "5.6.3"]
    [org.craigandera/dynne "0.4.1"]
  ]
  :main ^:skip-aot pixelsquiz.core
  :target-path ".target/%s"
  :profiles {:uberjar {:aot :all}}
  :jvm-opts ["--add-modules" "java.xml.bind" "-Djava.awt.headless=true"]
  :repl-options {
    :host "127.0.0.1"
    :port 7888
  }
)

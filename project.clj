(defproject pixelsquiz "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [spyscope "0.1.5"]
[org.clojars.gjahad/debug-repl "0.3.3"]
                 [reduce-fsm "0.1.4"]
                 [org.clojure/core.async "0.2.385"]
								 [com.taoensso/sente        "1.11.0-SNAPSHOT"] ; <--- Sente
								 [com.taoensso/timbre       "4.7.2"]
								 [http-kit                             "2.2.0"] ; Default
								 [ring                      "1.5.0"]
								 [ring/ring-defaults        "0.2.1"] ; Includes `ring-anti-forgery`, etc.
								 [compojure                 "1.5.1"] ; Or routing lib of your choice 
								 ]
  :main ^:skip-aot pixelsquiz.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

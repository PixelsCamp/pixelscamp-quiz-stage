(defproject pixelsquiz "0.1.0"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [spyscope "0.1.5"]
                  [org.clojars.gjahad/debug-repl "0.3.3"]
                 [reduce-fsm "0.1.4"]
                 [org.clojure/core.async "0.2.385"]
								 [http-kit                             "2.2.0"] ; Default
								 [ring                      "1.5.0"]
								 [ring/ring-defaults        "0.2.1"] ; Includes `ring-anti-forgery`, etc.
								 [compojure                 "1.5.1"] ; Or routing lib of your choice 
                 [org.clojars.torbjornvatn/hidapi "1.1"]
                 [clj-time "0.12.0"]
                 [cheshire "5.6.3"]
                 [org.craigandera/dynne "0.4.1"]
                 [prismatic/dommy "1.1.0"]
                 [cljs-ajax "0.5.8"]
								 ]
  :main ^:skip-aot pixelsquiz.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {
                   :source-paths ["dev"]
                   :dependencies [[org.clojure/tools.namespace "0.2.4"]]}}
  :plugins [[lein-figwheel "0.5.0-1"]
            [lein-cljsbuild "0.3.0"]]
  :cljsbuild {
              :builds [{
                        :id "quizconsole"
                        :source-paths ["src-cljs/quizconsole"]
                        :figwheel true
                        :compiler {:output-to "html/pixelsquiz/autogen/quizconsole.js"
                                   :optimizations :whitespace
                                   :pretty-print true
                                   }
                        }]
              }
  :jvm-opts ["--add-modules" "java.xml.bind"]
  )

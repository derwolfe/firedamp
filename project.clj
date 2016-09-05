(defproject firedamp "0.1.1-SNAPSHOT"
  :description "Bot that reports that status of Github, Codecov, and Travis APIs"
  :url "https://gitub.com/derwolfe/firedamp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [;; backend
                 [org.clojure/clojure "1.8.0"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [environ "1.0.3"]
                 [manifold "0.1.4"]

                 ;; web server
                 [aleph "0.4.1"]
                 [ring "1.5.0"]
                 [ring/ring-defaults "0.2.1"]

                 [compojure "1.5.1"]
                 [clj-time "0.12.0"]
                 [cheshire "5.6.3"]
                 [byte-streams "0.2.2"]
                 [hiccup "1.0.5"]

                 ;; metrics
                 [com.soundcloud/prometheus-clj "2.4.0"]

                 ;; front-back communication
                 [com.taoensso/sente "1.10.0"]
                 [com.taoensso/timbre "4.5.1"]

                 ;; frontend
                 [org.clojure/clojurescript "1.8.51"]
                 [reagent "0.6.0-rc"]]
  :plugins [[lein-auto "0.1.2"]
            [lein-cljfmt "0.3.0"]
            [lein-environ "1.0.3"]
            [lein-pprint "1.1.1"]
            [lein-environ "1.0.2"]
            [lein-ancient "0.6.10"]
            [jonase/eastwood "0.2.3"]
            [lein-cloverage "1.0.7-SNAPSHOT"]

            ;; frontend
            [lein-figwheel "0.5.6"]
            [lein-cljsbuild "1.1.4"]]
  :cljsbuild {:builds {:app {:id "firedamp"
                             :source-paths ["src/"]
                             :compiler {:output-to "resources/public/js/app.js"
                                        :output-dir "resources/public/js/out"
                                        :asset-path "js/out"
                                        :optimizations :none
                                        :pretty-print  true}}}}

  :figwheel {:http-server-root "public"
             :server-port 3449
             :nrepl-port 7002
             :css-dirs ["resources/public/css"]}

  :cljfmt {:indents {let-flow [[:inner 0]]
                     catch [[:inner 0]]}}
  :main ^:skip-aot firedamp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:dependencies [[com.cemerick/piggieback "0.2.1"]
                                  [org.clojure/tools.nrepl "0.2.10"]]
                   :repl-options {:nrepl-middleware
                                  [cemerick.piggieback/wrap-cljs-repl]}
                   :env {:port 8080}}})

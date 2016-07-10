(defproject firedamp "0.1.0-SNAPSHOT"
  :description "Bot that reports that status of Github, Codecov, and Travis APIs"
  :url "https://gitub.com/derwolfe/firedamp"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [environ "1.0.3"]
                 [com.taoensso/timbre "4.5.1"]
                 [adamwynne/feedparser-clj "0.5.2"]
                 [aleph "0.4.1"]
                 [manifold "0.1.4"]
                 [clj-time "0.12.0"]
                 [cheshire "5.6.3"]
                 [byte-streams "0.2.2"]
                 [twitter-api "0.7.8"]
                 [com.gfredericks/system-slash-exit "0.2.0"]
                 [com.cemerick/url "0.1.1"]]
  :plugins [[lein-auto "0.1.2"]
            [lein-cljfmt "0.3.0"]
            [lein-environ "1.0.3"]
            [lein-pprint "1.1.1"]
            [lein-environ "1.0.2"]
            [lein-ancient "0.6.10"]
            [jonase/eastwood "0.2.3"]
            [lein-cloverage "1.0.7-SNAPSHOT"]]
  :cljfmt {:indents {let-flow [[:inner 0]]
                     catch [[:inner 0]]}}
  :main ^:skip-aot firedamp.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}})

(set-env!
 :project 'firedamp
 :version "0.1.0-SNAPSHOT"
 :resource-paths #{"src"}
 :source-paths #{"test"}
 :dependencies '[[org.clojure/clojure "1.8.0" :scope "provided"]
                 [org.clojure/core.match "0.3.0-alpha4"]
                 [environ "1.0.3"]
                 [com.taoensso/timbre "4.5.1"]
                 [aleph "0.4.1"]
                 [manifold "0.1.4"]
                 [clj-time "0.12.0"]
                 [cheshire "5.6.3"]
                 [byte-streams "0.2.2"]
                 [twitter-api "0.7.8"]
                 [compojure "1.5.1"]
                 [com.soundcloud/prometheus-clj "2.4.0"]

                 ;; tests
                 [adzerk/boot-test "1.1.0" :scope "test"]
                 ])

(require '[adzerk.boot-test :refer :all])

(task-options!
 pom {:project (get-env :project)
      :version (get-env :version)
      :license {"Eclipse Public License"
                "http://www.eclipse.org/legal/epl-v10.html"}}
 aot {:namespace '#{firedamp.core}}
 jar {:main 'firedamp.core
      :manifest {"Description" "Bot that reports that status of Github, Codecov, and Travis APIs"
                 "Url" "https://github.com/derwolfe/firedamp.git"}}
 repl {:init-ns 'firedamp.core})

(deftask build
  []
  (comp (aot) (pom) (uber) (jar)))

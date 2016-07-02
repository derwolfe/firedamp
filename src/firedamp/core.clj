(ns firedamp.core
  (:require [byte-streams :as bs]
            [feedparser-clj.core :as feedparser]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [cheshire.core :as json])
  (:gen-class))

(defn parse-status-page
  [feed threshold]
  (let [parsed (feedparser/parse-feed feed)
        ;; get the most recent entries within threshold
        entries (:entries parsed)
        within-threshold (filter #(time/after? (time-coerce/from-date (:updated-date %)) threshold) entries)]
    within-threshold))

(defn parse-github
  [msg]
  (:status (json/parse-string msg true)))

(defn ^:private fetch-statuspage-io
  [url threshold]
  nil)

(defn fetch-travis-status
  [threshold]
  nil)

(defn fetch-codecov-status
  [threshold]
  nil)

(defn fetch-github-status
  []
  nil)

(defn red-alert
  [github codecov travis]
  nil)

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

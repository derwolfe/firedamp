(ns firedamp.core
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clj-time.coerce :as time-coerce]
   [clojure.core.reducers :as r]
   [environ.core :as env]
   [feedparser-clj.core :as feedparser]
   [manifold.deferred :as md]
   [manifold.time :as mt]
   [taoensso.timbre :as timbre]
   [twitter.oauth :as tw-auth]
   [twitter.api.restful :as tw-api])
  (:gen-class))

(def github "https://status.github.com/api/status.json")
(def codecov "http://status.codecov.io/history.atom")
(def travis "http://www.traviscistatus.com/history.atom")

(def state (atom {:alarm-state false
                  :last-update (time/now)}))

(defn parse-status-page
  [feed threshold]
  (let [parsed (feedparser/parse-feed feed)
        entries (:entries parsed)
        within-threshold  (filter
                           #(time/after? (time-coerce/from-date (:updated-date %)) threshold)
                           entries)]
    within-threshold))

(defn parse-github
  [msg]
  (:status msg))

(defn fetch-statuspage
  [url threshold]
  (timbre/info "fetching" url)
  (md/chain
   (http/get url)
   :body
   bs/to-input-stream
   (fn [stream]
     (timbre/info "got status page for" url)
     stream)))

(defn fetch-github-status
  []
  (timbre/info "fetching github")
  (md/chain
   (http/get github)
   :body
   bs/to-reader
   (fn [s]
     (timbre/info "got response from githb")
     (json/parse-stream s true))))

(defn red-alert?
  [github codecov travis]
  (not (and (= 0 (count travis))
            (= 0 (count codecov))
            (= "good" github))))

(defn tweet
  [message]
  (timbre/info "tweeting" message)
  (let [{:keys [api_key api_secret access_token access_secret]} env/env
        token (tw-auth/make-oauth-creds api_key
                                        api_secret
                                        access_token
                                        access_secret)]
    (tw-api/statuses-update :oauth-creds token
                            :params {:status message})))

(defn ugoh
  [period]
  (let [threshold-date (time/minus (time/now) (time/seconds period))]
    (md/let-flow [co (fetch-statuspage codecov threshold-date)
                  tr (fetch-statuspage travis threshold-date)
                  gh (fetch-github-status)]
      (let [parsed-co (parse-status-page co threshold-date)
            parsed-tr (parse-status-page tr threshold-date)
            parsed-gh (parse-github gh)
            new-state (red-alert? parsed-gh parsed-co parsed-tr)
            old-state (:alarm-state @state)]
        (cond
          (and (= new-state old-state) (not old-state))
          (timbre/info "still sunny")

          (and (= new-state old-state) old-state)
          (timbre/info "still dark")

          (and (not= new-state old-state) (not old-state))
          (do
            (timbre/info "sunny again")
            (tweet "sunny again"))

          (and (not= new-state old-state) old-state)
          (do
            (timbre/info "problem time")
            (tweet "problems")))
        (reset! state {:alarm-state state
                       :last-update (time/now)})))))

(defn keep-checking
  [period]
  (mt/every
   (* 1000 period) ;; ms -> sec
   (fn [] (ugoh period) true)))

(defn ^:private staying-alive
  []
  (.start (Thread. (fn [] (.join (Thread/currentThread))) "staying alive")))

(defn -main
  [& args]
  ;; check for the last 6 hours
  (staying-alive)
  (keep-checking (* 60 5)))

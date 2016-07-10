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
   [twitter.api.restful :as tw-api]
   [twitter.callbacks.handlers :as tw-handlers])
  (:import
   (twitter.callbacks.protocols SyncSingleCallback))
  (:gen-class))

(def github "https://status.github.com/api/status.json")
(def codecov "http://status.codecov.io/history.atom")
(def travis "http://www.traviscistatus.com/history.atom")

(def state (atom {:alarm-state false ;; start off assuming it all works
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

(defn fetch-github
  []
  (timbre/info "fetching github")
  (md/chain
   (http/get github)
   :body
   bs/to-reader
   (fn [s]
     (timbre/info "got response from github")
     (json/parse-stream s true))))

(defn red-alert?
  [github codecov travis]
  (not (and (= 0 (count travis))
            (= 0 (count codecov))
            (= "good" github))))

(defn tweet
  [message token]
  (timbre/info "tweeting" message)
  (md/future
    (tw-api/statuses-update :oauth-creds token
                            :params {:status message})))

(defn get-parse-statuses
  [period]
  "Fetch and parse the status messages for all of the providers.
   Returns a deferred that fire when parsing is finished."
  (let [threshold-date (time/minus (time/now) (time/seconds period))]
    (md/let-flow [co (fetch-statuspage codecov threshold-date)
                  tr (fetch-statuspage travis threshold-date)
                  gh (fetch-github)]
      (let [parsed-co (parse-status-page co threshold-date)
            parsed-tr (parse-status-page tr threshold-date)
            parsed-gh (parse-github gh)]
        {:codecov parsed-co
         :github parsed-gh
         :travis parsed-tr}))))

;; once repair is added this should be a state machine...
(defn decide
  [s0 s1]
  (cond
    (and (= s1 s0) (not s0)) ::sunny
    (and (= s1 s0) s0) ::dark
    (and (not= s1 s0) (not s0)) ::darkening
    (and (not= s1 s0) s0) ::brightening))

(defn alert
  [ctx parsed-statuses]
  (let [{:keys [alarm-state token]} ctx
        {:keys [codecov github travis]} parsed-statuses
        new-alarm-state (red-alert? github codecov travis)
        status (decide alarm-state new-alarm-state)]

    (timbre/info status)
    (when (= status ::darkening)
      (tweet "expect problems" token))

    (when (= status ::brightening)
      (tweet "repaired" token))

    (-> ctx
        (assoc :alarm-state status)
        (assoc :last-update (time/now)))))

(defn reset-world
  [period]
  (md/let-flow [statuses (get-parse-statuses period)]
    (reset! state (alert @state statuses))))

(defn keep-checking
  [period]
  (mt/every
   (* 1000 period) ;; ms -> sec
   (fn [] (reset-world period))))

(defn ^:private staying-alive
  []
  (.start (Thread. (fn [] (.join (Thread/currentThread))) "staying alive")))

(defn setup-twitter
  [env]
  (let [{:keys [api-key api-secret access-token access-token-secret]} env
        token (tw-auth/make-oauth-creds api-key
                                        api-secret
                                        access-token
                                        access-token-secret)]
    token))

(defn -main
  [& args]
  (staying-alive)
  (swap! state conj {:token (setup-twitter env/env)})
  (keep-checking (* 60 2)))

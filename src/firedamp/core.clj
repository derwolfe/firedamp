(ns firedamp.core
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clj-time.coerce :as time-coerce]
   [clojure.core.reducers :as r]
   [environ.core :as env]
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
(def codecov "https://wdzsn5dlywj9.statuspage.io/api/v2/status.json")
(def travis "https://pnpcptp8xh9k.statuspage.io/api/v2/status.json")

(def status-io-good "All Systems Operational")
(def github-good "good")

(def state (atom {:alarm-state false ;; start off assuming it all works
                  :last-update (time/now)}))

(defn parse-github
  [msg]
  (:status msg))

(defn parse-status-io
  [msg]
  (:description (:status msg)))

(defn fetch-json-status!
  [url]
  (timbre/info "fetching url" url)
  (md/chain
   (http/get url)
   :body
   bs/to-reader
   (fn [s]
     (timbre/info "got response from" url)
     (json/parse-stream s true))))

(defn get-parse-statuses!
  "Fetch and parse the status messages for all of the providers.
   Returns a deferred that fire when parsing is finished."
  []
  ;; these should all be able to timeout and show as failures
  (md/let-flow [codecov-status (fetch-json-status! codecov)
                travis-status (fetch-json-status! travis)
                github-status (fetch-json-status! github)]
    {:codecov (parse-status-io codecov-status)
     :travis (parse-status-io travis-status)
     :github (parse-github github-status)}))

(defn red-alert?
  [{:keys [github codecov travis]}]
  (not (and (= status-io-good codecov)
            (= status-io-good travis)
            (= github-good github))))

(defn get-next-state
  [s0 s1]
  (cond
    (and (= s1 s0) (not s0)) ::sunny
    (and (= s1 s0) s0) ::dark
    (and (not= s1 s0) (not s0)) ::darkening
    (and (not= s1 s0) s0) ::brightening))

(defn tweet!
  [message token]
  (timbre/info "tweeting" message)
  ;; XXX this might want a timeout value
  (md/future
    (tw-api/statuses-update :oauth-creds token
                            :params {:status message})))

(defn tweet-alert!
  [token status]
  (timbre/info status)
  (condp = status
    ::darkening (tweet! "expect problems" token)
    ::brightening (tweet! "should be back to normal" token)))

(defn alert!
  [ctx statuses]
  (let [{s0 :alarm-state token :token} ctx
        s1 (red-alert? statuses)
        status (get-next-state s0 s1)]
    (tweet-alert! token status)
    (-> ctx
        (assoc :alarm-state status)
        (assoc :last-update (time/now)))))

(defn run-world!
  []
  (md/let-flow [statuses (get-parse-statuses!)]
    (reset! state (alert! @state statuses))))

(defn keep-checking
  [period]
  (mt/every period run-world!))

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
  (keep-checking (mt/minutes 2)))

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
   (twitter.callbacks.protocols AsyncSingleCallback))
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
  (let [on-body (fn [response]
                  (timbre/info "tweet response"))
        result
        (tw-api/statuses-update :oauth-creds token
                                :params {:status message}
                                :callbacks (AsyncSingleCallback.
                                            tw-handlers/response-return-body
                                            tw-handlers/response-throw-error
                                            tw-handlers/exception-rethrow))]
    result))

(defn ugoh
  [ctx period]
  (let [threshold-date (time/minus (time/now) (time/seconds period))
        token (:token ctx)
        old-state (:alarm-state ctx)]
    (md/let-flow [co (fetch-statuspage codecov threshold-date)
                  tr (fetch-statuspage travis threshold-date)
                  gh (fetch-github)]
      (let [parsed-co (parse-status-page co threshold-date)
            parsed-tr (parse-status-page tr threshold-date)
            parsed-gh (parse-github gh)
            new-state (red-alert? parsed-gh parsed-co parsed-tr)]
        (timbre/info "s0" old-state "s1" new-state)
        (cond
          (and (= new-state old-state) (not old-state))
          (timbre/info "still sunny")

          (and (= new-state old-state) old-state)
          (timbre/info "still dark")

          (and (not= new-state old-state) (not old-state))
          (do
            (timbre/info "problem time")
            (md/->deferred (tweet "expect problems" token))))

          (and (not= new-state old-state) old-state)
          (do
            (timbre/info "sunny again")
            (md/->deferred (tweet "things are improving" token)))

          ;; return some new app state
          {:alarm-state state
           :token token
           :last-update (time/now)}))))

(defn reset-world
  [period]
  (md/let-flow [new-state (ugoh @state period)]
    (reset! state new-state)))

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
  (swap! state conj (setup-twitter env/env))
  (staying-alive)
  (keep-checking (* 60 2)))

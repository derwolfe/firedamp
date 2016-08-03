(ns firedamp.core
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [compojure.core :as cjc]
   [compojure.route :as cjr]
   [clj-time.core :as time]
   [clj-time.coerce :as time-coerce]
   [clojure.core.match :as cmatch]
   [clojure.core.reducers :as r]
   [environ.core :as env]
   [manifold.deferred :as md]
   [manifold.time :as mt]
   [taoensso.timbre :as timbre]
   [twitter.oauth :as tw-auth]
   [twitter.api.restful :as tw-api]
   [prometheus.core :as prometheus])
  (:import
   [java.util.concurrent TimeoutException])
  (:gen-class))

(def github "https://status.github.com/api/status.json")
(def codecov "https://wdzsn5dlywj9.statuspage.io/api/v2/status.json")
(def travis "https://pnpcptp8xh9k.statuspage.io/api/v2/status.json")

(def status-io-good "All Systems Operational")
(def github-good "good")

(def bad-fetch-msg "problem fetching")

(def timeout-after (mt/seconds 10))

(def state (atom {:alarm-state ::good
                  :last-update (time/now)}))

(def store (atom nil))

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
   (md/timeout! (http/get url) timeout-after)
   :body
   bs/to-reader
   (fn [s]
     (timbre/info "got response from" url)
     (json/parse-stream s true))))

(defn get-parse-statuses!
  "Fetch and parse the status messages for all of the providers.
   Returns a deferred that fire when parsing is finished."
  []
  (let [parse-status
        (fn [status url]
          (condp = url
            codecov (parse-status-io status)
            travis (parse-status-io status)
            github (parse-github status)))

        fetch-and-parse!
        (fn [url]
          (->
           (md/chain (fetch-json-status! url)
                     #(parse-status % url))
           (md/catch
            TimeoutException
            (fn [exc]
              (timbre/warnf "fetching %s timed out: %s" url exc)
              bad-fetch-msg))))]
    (md/let-flow [codecov-status (fetch-and-parse! codecov)
                  travis-status (fetch-and-parse! travis)
                  github-status  (fetch-and-parse! github)]
      {:codecov codecov-status
       :travis travis-status
       :github github-status})))

(defn red-alert?
  [{:keys [github codecov travis]}]
  (if (and (= status-io-good codecov)
           (= status-io-good travis)
           (= github-good github))
    ::good
    ::bad))

(defn get-next-state
  [s0 s1]
  (cmatch/match [s0 s1]
    [::good ::bad] ::darkening
    [::good ::good] ::sunny
    [::bad ::bad] ::dark
    [::bad ::good] ::brightening))

(defn setup-twitter
  [env]
  (let [{:keys [api-key api-secret access-token access-token-secret]} env
        token (tw-auth/make-oauth-creds api-key
                                        api-secret
                                        access-token
                                        access-token-secret)]
    token))

(defn tweet!
  [message]
  (timbre/info "tweeting" message)
  (let [token (setup-twitter env/env)]
    (->
     (md/chain
      ;; this should also be able to timeout
      (md/future (tw-api/statuses-update :oauth-creds token :params {:status message}))
      (fn [_] (timbre/info "tweeted")))
     (md/catch
      Exception
      (fn [exc] (timbre/warn "exception while tweeting:" exc))))))

(defn tweet-alert!
  [status]
  (timbre/info "tweet alert" status)
  (condp = status
    ::darkening (tweet! "expect problems @chriswwolfe")
    ::brightening (tweet! "should be back to normal @chriswwolfe")
    ;; else
    (md/success-deferred ::no-tweet)))

(defn alert!
  [ctx statuses]
  (let [{s0 :alarm-state} ctx
        s1 (red-alert? statuses)
        tweet-status (get-next-state s0 s1)]
    (md/chain
     (tweet-alert! tweet-status)
     (fn [arg]
       (-> ctx
           (assoc :alarm-state s1)
           (assoc :last-update (time/now)))))))

(defn run-world!
  []
  (let [old-state @state]
    (md/chain
     (get-parse-statuses!)
     #(alert! @state %)
     (fn [new-world]
       (reset! state new-world)
       (timbre/infof "s0=%s, s1=%s" (:alarm-state old-state) (:alarm-state @state))))))

(defn keep-checking
  [period]
  (mt/every period run-world!))

(defn ^:private staying-alive
  []
  (.start (Thread. (fn [] (.join (Thread/currentThread))) "staying alive")))

(defn metrics-handler [_]
  (prometheus/increase-counter @store "test" "some_counter" ["bar"] 3)
  (prometheus/set-gauge @store "test" "some_gauge" 101 ["bar"])
  (prometheus/track-observation @store "test" "some_histogram" 0.71 ["bar"])
  (prometheus/dump-metrics (:registry @store)))

;; come up with some metrics?
(defn register-metrics [store]
  (->
   store
   (prometheus/register-counter "test" "some_counter" "some test" ["foo"])
   (prometheus/register-gauge "test" "some_gauge" "some test" ["foo"])
   (prometheus/register-histogram "test" "some_histogram" "some test" ["foo"] [0.7 0.8 0.9])))

(defn init-metrics! []
  (->> (prometheus/init-defaults)
       (register-metrics)
       (reset! store)))

(cjc/defroutes app
  (cjc/GET "/metrics" [] metrics-handler)
  (cjr/not-found "404: Not Found"))

(defn -main
  [& args]
  (init-metrics!)
  (http/start-server app {:port 8080 :host "0.0.0.0"})
  (keep-checking (mt/minutes 2))
  (staying-alive))

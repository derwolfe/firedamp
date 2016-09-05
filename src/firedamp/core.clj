(ns firedamp.core
  (:require
   [aleph.http :as http]
   [byte-streams :as bs]
   [cheshire.core :as json]
   [compojure.core :as cjc]
   [compojure.route :as cjr]
   [clj-time.core :as time]
   [clj-time.coerce :as time-coerce]
   [clj-time.format :as time-format]
   [clojure.core.match :as cmatch]
   [clojure.core.reducers :as r]
   [environ.core :refer [env]]
   [hiccup.page :refer [html5 include-js include-css]]
   [manifold.deferred :as md]
   [manifold.time :as mt]
   [ring.middleware.params :refer [wrap-params]]
   [ring.middleware.keyword-params :refer [wrap-keyword-params]]
   [ring.middleware.resource :refer [wrap-resource]]
   [taoensso.timbre :as timbre]
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.aleph :refer [get-sch-adapter]]
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
                  :statuses nil
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
        bump-metric (fn [out url]
                      (prometheus/increase-counter @store "firedamp" "status_checks" [url "success"] 1.0)
                      out)
        fetch-and-parse!
        (fn [url]
          (->
           (md/chain (fetch-json-status! url)
                     #(bump-metric % url)
                     #(parse-status % url))
           (md/catch
            TimeoutException
            (fn [exc]
              (timbre/warnf "fetching %s timed out: %s" url exc)
              (prometheus/increase-counter @store "firedamp" "status_checks" [url "failure"] 1.0)
              bad-fetch-msg))))]
    (md/let-flow [codecov-status (fetch-and-parse! codecov)
                  travis-status (fetch-and-parse! travis)
                  github-status  (fetch-and-parse! github)]
      {:codecov codecov-status
       :travis travis-status
       :github github-status})))

(defn down?
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

(defn alert!
  [ctx statuses]
  (let [{s0 :alarm-state} ctx
        s1 (down? statuses)
        status (get-next-state s0 s1)]
    (-> ctx
        (assoc :alarm-state s1)
        (assoc :statuses statuses)
        (assoc :last-update (time/now)))))

(defn run-world!
  []
  (let [old-state @state]
    (md/chain
     (get-parse-statuses!)
     #(alert! @state %)
     (fn [new-world]
       (timbre/info (:statuses new-world))
       (reset! state new-world)
       (timbre/infof "s0=%s, s1=%s" (:alarm-state old-state) (:alarm-state @state))))))

(defn keep-checking
  [period]
  (mt/every period run-world!))

(defn ^:private staying-alive
  []
  (.start (Thread. (fn [] (.join (Thread/currentThread))) "staying alive")))

(defn metrics-handler [_]
  (prometheus/increase-counter @store "firedamp" "inbound_requests" ["/metrics" "GET"] 1.0)
  (prometheus/dump-metrics (:registry @store)))

;; come up with some metrics?
(defn register-metrics [store]
  (-> store
      (prometheus/register-counter
       "firedamp"
       "status_checks"
       "status checks"
       ["url" "success"])
      (prometheus/register-counter
       "firedamp"
       "inbound_requests"
       "count the number of inbound requests to a given endpoint"
       ["endpoint" "method"])))

(defn init-metrics! []
  (->> (prometheus/init-defaults)
       (register-metrics)
       (reset! store)))

(def built-in-formatter (time-format/formatters :basic-date-time))

;; main page
(def home-page
  (html5
   [:html
    [:head
     [:title "Firedamp"]
     [:meta {:charset "utf-8"}]
     [:meta {:name "viewport"
             :content "width=device-width, initial-scale=1"}]]
    [:body
     [:div#app]
     (include-js "/js/out/goog/base.js")
     (include-js "/js/app.js")]]))

(defn status-handler
  [_req]
  (let [{:keys [statuses last-update alarm-state]} @state
        body {:statuses statuses
              :alarm-state alarm-state
              :last-update (time-coerce/to-date last-update)}
        as-json (json/generate-string body {:pretty true})]
    (prometheus/increase-counter
     @store "firedamp"
     "inbound_requests"
     ["/" "GET"]
     1.0)
    {:status 200
     :headers {"content-type" "application/json"}
     :body as-json}))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket! (get-sch-adapter) {})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(cjc/defroutes routes
  (cjc/GET "/status" [] status-handler)
  (cjc/GET "/metrics" [] metrics-handler)

  (cjc/GET "/" [] home-page)
  (cjr/resources "/")

  (cjc/GET "/chsk" req (ring-ajax-get-or-ws-handshake req))
  (cjc/POST "/chsk" req (ring-ajax-post req))
  (cjr/not-found "404: Not Found"))

(def app
  (-> routes
      wrap-keyword-params
      wrap-params))

(defn -main
  [& args]
  (let [port (Integer/parseInt (:port env))]
    (init-metrics!)
    (http/start-server app {:port port :host "0.0.0.0"})
    (keep-checking (mt/minutes 2))
    (staying-alive)))

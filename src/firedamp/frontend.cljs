(ns firedamp.frontend
  (:require
   ;;[cljs.core.async :as async :refer [<! >! put! chan]]
   [taoensso.timbre :as timbre]
   [cljs-time.core :as time]
   ;;[taoensso.sente :as sente :refer [cb-success?]]
   [reagent.core :as reagent]))

;; (let [{:keys [chsk ch-recv send-fn state]}
;;       (sente/make-channel-socket! "/chsk" {:type :auto})]
;;   (def chsk chsk)
;;   (def ch-chsk ch-recv)
;;   (def chsk-send! send-fn)
;;   (def chsk-state state))

;; (defn push-msg-handler
;;   [data]
;;   (if (nil? data)
;;     (timbre/info "nada")
;;     (reset! state data)))

;; ;; ws plumbing
;; (defmethod event-msg-handler :chsk/recv
;;   [{:as ev-msg :keys [?data]}]
;;   (push-msg-handler ?data))

;; (defmulti event-msg-handler :id) ; Dispatch on event-id

;; (defmethod event-msg-handler :default ; Fallback
;;   [{:as ev-msg :keys [event]}]
;;   (js/console.log "Unhandled event: %s" (pr-str event)))

;; (defmethod event-msg-handler :chsk/state
;;   [_old new]
;;   (if (= (:?data new-ev-msg) {:first-open? true})
;;     (timbre/info "Channel socket successfully established!")
;;     (timbre/info "Channel socket state change: %s" new)))

;; (defmethod event-msg-handler :chsk/recv
;;   [{:as ev-msg :keys [?data]}]
;;   (push-msg-handler ?data))

;; (defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
;;   (event-msg-handler ev-msg))

;; the state atom, use it, and _only_ it
(def state (reagent/atom
            {:statuses {:a 1 :b 2}
             :last-update (time/now)
             :alarm-state "good"}))

;; views
(defn header
  []
  [:h1 "Firedamp"]
  [:p
   [:h3 "Firedamp tells you the status of multiple statuses"]])

(defn status-view
  [{:keys [statuses alarm-state last-update]}]
  [:h1 alarm-state]
  [:h3 "The statuses"]
  (doseq [[k v] statuses]
    [:p k v]))

;; setting up the front end
(defn app []
  [header]
  [status-view state])

(defn ^:export run []
  (reagent/render-component [app] (.getElementById js/document "app")))

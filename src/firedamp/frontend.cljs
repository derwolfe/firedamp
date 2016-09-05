(ns firedamp.frontend
  (:require
   [cljs.core.async :as async :refer [<! >! put! chan]]
   [taoensso.sente :as sente :refer [cb-success?]]
   [reagent.core :as reagent])
  (:require-macros
   [cljs.core.async.macros :as asyncm :refer [go go-loop]]))

(def state (reagent/atom {}))


;; stupid simple views
(defn status-view
  [data]
  [:div
   [:h2 "Current Status"
    [data]]])


(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket! "/chsk" {:type :auto})]
  (def chsk chsk)
  (def ch-chsk    ch-recv)
  (def chsk-send! send-fn)
  (def chsk-state state))


(defn push-msg-handler
  [data]
  (if (nil? data)
    (timbre/info "nada")
    (reset! state data)))

;; ws plumbing
(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (push-msg-handler ?data))

(defmulti event-msg-handler :id) ; Dispatch on event-id

(defmethod event-msg-handler :default ; Fallback
  [{:as ev-msg :keys [event]}]
  (js/console.log "Unhandled event: %s" (pr-str event)))

(defmethod event-msg-handler :chsk/state
  [_old new]
  (if (= (:?data new-ev-msg) {:first-open? true})
    (timbre/info "Channel socket successfully established!")
    (timbre/info "Channel socket state change: %s" new)))

(defmethod event-msg-handler :chsk/recv
  [{:as ev-msg :keys [?data]}]
  (push-msg-handler ?data))

(defn event-msg-handler* [{:as ev-msg :keys [id ?data event]}]
  (event-msg-handler ev-msg))

;; setting up the front end
(defn app [data]
  (:re-render-flip @data)
  [status-view data])

(defn ^:export main []
  (when-let [root (.getElementById js/document "app")]
    (reagent/render-component [app state] root)))

(ns firedamp.frontend
  (:require
   [taoensso.timbre :as timbre]
   [cljs-time.core :as time]
   [cljs-time.format :as format]
   [reagent.core :as r]))

;; the state atom, use it, and _only_ it
(def app-state (r/atom
                {:statuses {:a 1 :b 2}
                 :last-update (time/now)
                 :alarm-state "good"}))

(defn ->date-str
  [d]
  (format/unparse (format/formatters :ordinal-date-time-no-ms) d))

(defn status-view
  [data]
  (let [{:keys [statuses last-update alarm-state]} @data]
    [:div
     [:h1 "Firedamp"]
     [:h3 "Statuses"]
     [:p "Last updated at: " (->date-str last-update)]
     [:ul
      (for [[k v] statuses]
        ^{:key k} [:li (name k) " - " v])]]))

(defn app
  [state]
  [status-view state])

(defn ^:export run []
  (r/render-component [app app-state] (.getElementById js/document "app")))

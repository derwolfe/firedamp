(ns firedamp.frontend
  (:require
   [taoensso.timbre :as timbre]
   [cljs-time.core :as time]
   [reagent.core :as r]))

;; the state atom, use it, and _only_ it
(def app-state (r/atom
                {:statuses {:a 1 :b 2}
                 :last-update (time/now)
                 :alarm-state "good"}))

;; views
(defn header
  []
  [:h1 "Firedamp"])

(defn status-view
  [data]
  (let [{:keys [statuses last-update alarm-state]} @data]
    [:div
     [:h3 "Statuses"]
     [:p "Last updated at:" (str last-update)]
     [:ul
      (for [[k v] statuses]
        ^{:key k} [:li (str k) " - " v])]]))

(defn app
  [state]
  [:div
   [header]
   [status-view state]])

(defn ^:export run []
  (r/render-component [app app-state] (.getElementById js/document "app")))

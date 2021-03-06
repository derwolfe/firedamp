(ns firedamp.core-test
  (:require
   [byte-streams :as bs]
   [clojure.test :refer :all]
   [clojure.java.io :as io]
   [cheshire.core :as json]
   [clj-time.core :as time]
   [clj-time.coerce :as time-coerce]
   [firedamp.core :as core]
   [manifold.deferred :as md]
   [manifold.time :as mt]
   [manifold.stream :as ms])
  (:import
   [java.util.concurrent TimeoutException]))

(defn status-clj
  [status when]
  {:page
   {:id "pnpcptp8xh9k"
    :name "Travis CI"
    :url "https://www.traviscistatus.com",
    :updated_at (time-coerce/to-long when)},
   :status
   {:indicator "none"
    :description status}})

(defn github-clj
  [status when]
  {:status status :last-updated (time-coerce/to-long when)})

;; need tests for each success/failure case
(deftest parse-status-tests
  (testing "parses status-io (tr)"
    (let [bad (status-clj "Oh shit" (time/now))
          good (status-clj "All Systems Operational" (time/now))]
      (is (= "All Systems Operational" (core/parse-status-io good)))
      (is (= "Oh shit" (core/parse-status-io bad)))))
  (testing "parses github status"
    (let [bad (github-clj "bad" (time/now))
          good (github-clj "good" (time/now))]
      (is (= "bad" (core/parse-github bad)))
      (is (= "good" (core/parse-github good))))))

(deftest fetch-tests
  ;; XXX parametrize this for the urls
  (testing "fetches urls and returns data when available"
    (let [stream-response (json/generate-string (github-clj "bad" (time/now)))
          expected (json/parse-string stream-response true)
          s (->> stream-response (map str) ms/->source)
          hits (atom [])
          fake-get (fn [url]
                     (swap! hits conj url)
                     {:body s})]
      (with-redefs [aleph.http/get fake-get]
        (is (= expected @(core/fetch-json-status! core/github)))
        (is (= [core/github] @hits)))))
  (testing "throws TimeoutException when requesting takes too long"
    (let [hits (atom [])
          fake-get (fn [url]
                     (swap! hits conj url)
                     (mt/in
                      (mt/seconds (* 2 core/timeout-after))
                      #(fn [] {})))
          clock (mt/mock-clock)]
      (with-redefs [aleph.http/get fake-get]
        (mt/with-clock clock
          (let [result (core/fetch-json-status! core/github)]
            (mt/advance clock (mt/seconds core/timeout-after))
            (is (thrown-with-msg? TimeoutException
                                  #"timed out after 10000.0 milliseconds" @result))
            (is (= [core/github] @hits))))))))

(deftest get-next-state
  ;; this should use are
  (is (= :firedamp.core/sunny
         (core/get-next-state :firedamp.core/good :firedamp.core/good)))
  (is (= :firedamp.core/dark
         (core/get-next-state :firedamp.core/bad :firedamp.core/bad)))
  (is (= :firedamp.core/darkening
         (core/get-next-state :firedamp.core/good :firedamp.core/bad)))
  (is (= :firedamp.core/brightening
         (core/get-next-state :firedamp.core/bad :firedamp.core/good))))

;; (defn get-metrics
;;   [store]
;;   (let [checks (-> (:metrics store)
;;                    (get "firedamp")
;;                    (get "status_checks")
;;                    (.collect))]
;;     ))


(deftest get-parse-statuses!-tests
  (testing "happy path - returns a deferred wrapping a map of statuses"
    (let [_metrics-store (core/init-metrics!)
          return-status (fn [status] (md/success-deferred status))
          now (time/now)
          fake-json-status
          (fn [url]
            (condp = url
              core/github
              (return-status (github-clj core/github-good now))

              core/travis
              (return-status (status-clj core/status-io-good now))

              core/codecov
              (return-status (status-clj core/status-io-good now))))]
      (with-redefs [firedamp.core/fetch-json-status! fake-json-status]
        (is (= {:codecov core/status-io-good
                :travis core/status-io-good
                :github core/github-good}
               @(core/get-parse-statuses!))))))
  (testing "returns data when fetching timed out"
    (let [_metrics-store (core/init-metrics!)
          calls (atom [])
          fake-get (fn [url]
                     (mt/in
                      (mt/seconds (* 2 core/timeout-after))
                      (fn [url] (swap! calls conj url))))
          clock (mt/mock-clock)]
      (with-redefs [aleph.http/get fake-get]
        (mt/with-clock clock
          (let [result (core/get-parse-statuses!)]
            (mt/advance clock (mt/seconds core/timeout-after))
            (is (= {:codecov core/bad-fetch-msg
                    :travis core/bad-fetch-msg
                    :github core/bad-fetch-msg}
                   @result))
            (is (= 0 (count @calls)))))))))

;; (deftest alert-tests
;;   (testing "alerts "
;;     (let [fake-tweet (fn [msg token] (md/success-deferred ::done))]
;;       (with-redefs [firedamp.core/tweet fake-tweet]
;;         (let [s {:codecov [::bad]
;;                  :github "good"
;;                  :travis []}
;;               ctx {:token "12345"
;;                    :last-update (time/now)
;;                    :alarm-state false}
;;               new-state (core/alert ctx s)
;;               {:keys [alarm-state last-update token]} new-state]
;;           (is alarm-state)
;;           (is (time/after? last-update (:last-update ctx)))
;;           (is (= token (:token ctx))))))))
;; (testing "alerts with github bad")
;; (testing "alerts with travis events")
;; (testing "says repaired when new event is a repair")))
;; (defn tweet-tests;;   (testing "it tries to tweet"))

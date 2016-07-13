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
   [manifold.stream :as ms]))

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
  (testing "fetches urls"
    (let [stream-response (json/generate-string (github-clj "bad" (time/now)))
          expected (json/parse-string stream-response true)
          s (->> stream-response (map str) ms/->source)
          hits (atom [])
          fake-get (fn [url]
                     (swap! hits conj url)
                     {:body s})]
      (with-redefs [aleph.http/get fake-get]
        (is (= expected @(core/fetch-json-status! core/github)))
        (is (= [core/github] @hits))))))

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

(deftest get-parse-statuses!-tests
  ;; parse and fetch are already independently tested
  (testing "returns a deferred wrapping a map of statuses"
    (let [return-status (fn [status] (md/success-deferred status))
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
               @(core/get-parse-statuses!)))))))

(deftest tweet-alert!
  (let [toot (atom {})
        t "token"
        fake-tweet (fn [message token]
                     (reset! toot {:message message :token t}))]
    (with-redefs [firedamp.core/tweet! fake-tweet]
      (testing "tweets when problem"
        (core/tweet-alert! t :firedamp.core/darkening)
        (is (= "expect problems" (:message @toot)))
        (is (= t (:token @toot))))
      (testing "tweets when repaired"
        (core/tweet-alert! t :firedamp.core/brightening)
        (is (= "should be back to normal" (:message @toot)))
        (is (= t (:token @toot)))))))

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

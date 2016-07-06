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

;; need tests for each success/failure case
(deftest parse-status-tests
  (testing "reads codecov feed"
    (let [feed-stream (-> "test/codecov.atom"
                          io/resource
                          io/input-stream)
          ;; as of this date, the atom feed contains only a single event
          with-events (time/date-time 2016 6 30 15 16 0)
          parsed (core/parse-status-page feed-stream with-events)]
      (is (= 1 (count parsed)))))
  (testing "reads travis feed"
    (let [feed-stream (-> "test/travis.atom"
                          io/resource
                          io/input-stream)
          ;; as of this date, the atom feed contains only a single event
          with-events (time/date-time 2016 6 30 15 16 0)
          parsed (core/parse-status-page feed-stream with-events)]
      (is (= 1 (count parsed)))))
  (testing "parses github status"
    (let [bad {:status "bad"}
          good {:status "good"}]
      (is (= "bad" (core/parse-github bad)))
      (is (= "good" (core/parse-github good))))))

(defn github-json [status when]
  (json/generate-string {:status status :last-updated (time-coerce/to-long when)}))

(deftest fetch-tests
  (testing "fetches statuspages"
    (let [stream-response "heedly mcskniverson\nwent to the market"
          s (->> stream-response (map str) ms/->source)
          hits (atom [])
          fake-get (fn [url]
                     (swap! hits conj url)
                     {:body s})]
      (with-redefs [aleph.http/get fake-get]
        (is (= stream-response (bs/to-string @(core/fetch-statuspage core/travis (time/now)))))
        (is (= [core/travis] @hits)))))
  (testing "fetches github")
  (let [stream-response (github-json "bad" (time/now))
        expected (json/parse-string stream-response true)
        s (->> stream-response (map str) ms/->source)
        hits (atom [])
        fake-get (fn [url]
                   (swap! hits conj url)
                   {:body s})]
    (with-redefs [aleph.http/get fake-get]
      (is (= expected @(core/fetch-github)))
      (is (= [core/github] @hits)))))

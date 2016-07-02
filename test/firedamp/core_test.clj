(ns firedamp.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [cheshire.core :as json]
            [clj-time.core :as time]
            [clj-time.coerce :as time-coerce]
            [firedamp.core :as core]))

(defn github-json [status when]
  (json/generate-string {:status status :last-updated (time-coerce/to-long when)}))

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
    (let [bad (github-json "bad" (time/now))
          good (github-json "good" (time/now))]
      (is (= "bad" (core/parse-github bad)))
      (is (= "good" (core/parse-github good))))))

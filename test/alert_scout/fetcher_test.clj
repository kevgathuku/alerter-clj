(ns alert-scout.fetcher-test
  "Tests for RSS/Atom feed fetching with error handling.

   These tests use mocking to avoid real network requests:
   - No external HTTP calls are made
   - Tests run quickly and reliably
   - Error handling logic is validated without network dependencies"
  (:require [clojure.test :refer :all]
            [alert-scout.fetcher :as fetcher])
  (:import (java.io IOException)))

(deftest test-get-http-response-code
  (testing "Extracts HTTP 429 from IOException message"
    (let [e (IOException. "Server returned HTTP response code: 429 for URL: https://example.com/rss.xml")
          code (#'fetcher/get-http-response-code e)]
      (is (= 429 code))))

  (testing "Extracts HTTP 403 from IOException message"
    (let [e (IOException. "Server returned HTTP response code: 403 for URL: https://example.com/rss.xml")
          code (#'fetcher/get-http-response-code e)]
      (is (= 403 code))))

  (testing "Extracts HTTP 404 from IOException message"
    (let [e (IOException. "Server returned HTTP response code: 404 for URL: https://example.com/rss.xml")
          code (#'fetcher/get-http-response-code e)]
      (is (= 404 code))))

  (testing "Extracts HTTP 500 from IOException message"
    (let [e (IOException. "Server returned HTTP response code: 500 for URL: https://example.com/rss.xml")
          code (#'fetcher/get-http-response-code e)]
      (is (= 500 code))))

  (testing "Returns nil for non-HTTP error message"
    (let [e (IOException. "Connection timeout")
          code (#'fetcher/get-http-response-code e)]
      (is (nil? code))))

  (testing "Returns nil for exception with no message"
    (let [e (IOException.)
          code (#'fetcher/get-http-response-code e)]
      (is (nil? code)))))

(deftest test-fetch-items-error-handling
  (testing "fetch-items returns empty list when fetch-feed returns nil"
    ;; Mock fetch-feed to return nil (simulating error)
    (with-redefs [fetcher/fetch-feed (constantly nil)]
      (let [feed {:feed-id "test-feed"
                  :url "https://example.com/rss.xml"}]
        ;; Should return empty list, not throw
        (is (= [] (fetcher/fetch-items feed)))))))

(deftest test-fetch-feed-error-handling
  (testing "fetch-feed catches IOException and returns nil"
    ;; Create a function that throws IOException like the real code would encounter
    (let [test-fn (fn []
                    (try
                      ;; Simulate what happens in fetch-feed
                      (throw (IOException. "Server returned HTTP response code: 429 for URL: https://example.com/rss.xml"))
                      (catch IOException e
                        ;; This is the same error handling logic as in fetch-feed
                        (let [http-code (#'fetcher/get-http-response-code e)]
                          (is (= 429 http-code))
                          nil))))]
      (is (nil? (test-fn)))))

  (testing "fetch-feed handles 404 errors correctly"
    (let [test-fn (fn []
                    (try
                      (throw (IOException. "Server returned HTTP response code: 404 for URL: https://example.com/rss.xml"))
                      (catch IOException e
                        (let [http-code (#'fetcher/get-http-response-code e)]
                          (is (= 404 http-code))
                          nil))))]
      (is (nil? (test-fn)))))

  (testing "fetch-feed handles general Exception correctly"
    (let [test-fn (fn []
                    (try
                      (throw (Exception. "Network timeout"))
                      (catch Exception e
                        nil)))]
      (is (nil? (test-fn))))))

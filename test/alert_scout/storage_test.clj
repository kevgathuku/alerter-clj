(ns alert-scout.storage-test
  (:require [clojure.test :refer :all]
            [alert-scout.storage :as storage]
            [alert-scout.schemas :as schemas]
            [clojure.java.io :as io])
  (:import (java.util Date)))

(def test-feeds-path "test-resources/test-feeds.edn")
(def test-users-path "test-resources/test-users.edn")
(def test-rules-path "test-resources/test-rules.edn")

(defn create-test-dir []
  (.mkdirs (io/file "test-resources")))

(defn cleanup-test-files []
  (doseq [path [test-feeds-path test-users-path test-rules-path]]
    (when (.exists (io/file path))
      (io/delete-file path))))

(use-fixtures :each
  (fn [f]
    (create-test-dir)
    (cleanup-test-files)
    (f)
    (cleanup-test-files)))

(deftest test-load-feeds-validation
  (testing "Valid feeds load successfully"
    (spit test-feeds-path "[{:feed-id \"hn\" :url \"https://news.ycombinator.com/rss\"}]")
    (let [feeds (storage/load-feeds test-feeds-path)]
      (is (= 1 (count feeds)))
      (is (= "hn" (:feed-id (first feeds))))))

  (testing "Invalid feeds throw validation error"
    (spit test-feeds-path "[{:feed-id \"\" :url \"\"}]")
    (is (thrown-with-msg? Exception #"Invalid feeds"
                          (storage/load-feeds test-feeds-path))))

  (testing "Empty file returns empty vector"
    (spit test-feeds-path "[]")
    (is (= [] (storage/load-feeds test-feeds-path)))))

(deftest test-add-feed
  (testing "Add valid feed to empty file"
    (spit test-feeds-path "[]")
    (let [updated (storage/add-feed! test-feeds-path "test" "https://example.com/rss")]
      (is (= 1 (count updated)))
      (is (= "test" (:feed-id (first updated))))))

  (testing "Add feed to existing feeds"
    (spit test-feeds-path "[{:feed-id \"hn\" :url \"https://news.ycombinator.com/rss\"}]")
    (let [updated (storage/add-feed! test-feeds-path "blog" "https://blog.example.com/rss")]
      (is (= 2 (count updated)))
      (is (some #(= "blog" (:feed-id %)) updated))))

  (testing "Adding invalid feed throws error"
    (spit test-feeds-path "[]")
    (is (thrown? Exception
                 (storage/add-feed! test-feeds-path "" "")))))

(deftest test-remove-feed
  (testing "Remove existing feed"
    (spit test-feeds-path "[{:feed-id \"hn\" :url \"https://news.ycombinator.com/rss\"}
                            {:feed-id \"blog\" :url \"https://blog.example.com/rss\"}]")
    (let [updated (storage/remove-feed! test-feeds-path "hn")]
      (is (= 1 (count updated)))
      (is (= "blog" (:feed-id (first updated))))))

  (testing "Remove non-existent feed returns unchanged list"
    (spit test-feeds-path "[{:feed-id \"hn\" :url \"https://news.ycombinator.com/rss\"}]")
    (let [updated (storage/remove-feed! test-feeds-path "nonexistent")]
      (is (= 1 (count updated)))
      (is (= "hn" (:feed-id (first updated)))))))

(deftest test-get-feed
  (testing "Get existing feed"
    (spit test-feeds-path "[{:feed-id \"hn\" :url \"https://news.ycombinator.com/rss\"}
                            {:feed-id \"blog\" :url \"https://blog.example.com/rss\"}]")
    (let [feed (storage/get-feed test-feeds-path "blog")]
      (is (= "blog" (:feed-id feed)))
      (is (= "https://blog.example.com/rss" (:url feed)))))

  (testing "Get non-existent feed returns nil"
    (spit test-feeds-path "[{:feed-id \"hn\" :url \"https://news.ycombinator.com/rss\"}]")
    (is (nil? (storage/get-feed test-feeds-path "nonexistent")))))

(deftest test-load-users-validation
  (testing "Valid users load successfully"
    (spit test-users-path "[{:id \"alice\" :email \"alice@example.com\"}]")
    (let [users (storage/load-users test-users-path)]
      (is (= 1 (count users)))
      (is (= "alice" (:id (first users))))))

  (testing "Invalid email throws validation error"
    (spit test-users-path "[{:id \"alice\" :email \"not-an-email\"}]")
    (is (thrown-with-msg? Exception #"Invalid users"
                          (storage/load-users test-users-path)))))

(deftest test-load-rules-validation
  (testing "Valid rules load successfully"
    (spit test-rules-path "[{:id \"test-rule\" :user-id \"alice\" :must [\"rails\"]}]")
    (let [rules (storage/load-rules test-rules-path)]
      (is (= 1 (count rules)))
      (is (= "test-rule" (:id (first rules))))))

  (testing "Invalid rule throws validation error"
    (spit test-rules-path "[{:id \"\" :user-id \"alice\"}]")
    (is (thrown-with-msg? Exception #"Invalid rules"
                          (storage/load-rules test-rules-path)))))

(deftest test-checkpoint-management
  (testing "Checkpoint operations"
    (let [checkpoint-path "test-resources/test-checkpoints.edn"]
      (try
        ;; Create empty file first
        (spit checkpoint-path "{}")

        ;; Initialize
        (storage/load-checkpoints! checkpoint-path)

        ;; Initially no checkpoint
        (is (nil? (storage/last-seen "hn")))

        ;; Update checkpoint
        (let [now (Date.)]
          (storage/update-checkpoint! "hn" now checkpoint-path)
          (is (= now (storage/last-seen "hn"))))

        ;; Update another feed
        (let [now2 (Date.)]
          (storage/update-checkpoint! "blog" now2 checkpoint-path)
          (is (= now2 (storage/last-seen "blog"))))

        (finally
          (when (.exists (io/file checkpoint-path))
            (io/delete-file checkpoint-path)))))))

(deftest test-save-alerts-markdown
  (testing "Save alerts to markdown format"
    (let [alerts [{:user-id "kevin"
                   :rule-id "rails-api"
                   :item {:feed-id "hn"
                          :title "Building Rails API"
                          :link "https://example.com/test"
                          :published-at (Date.)}
                   :excerpts [{:text "Building Rails API"
                              :matched-terms ["rails" "api"]
                              :source :title}]}]
          temp-file "test-resources/test-alerts.md"]
      (try
        ;; Save to file
        (storage/save-alerts! alerts temp-file :markdown)

        ;; Verify file exists
        (is (.exists (io/file temp-file)))

        ;; Read and verify content
        (let [content (slurp temp-file)]
          ;; Should contain markdown formatting
          (is (.contains content "# Alert Scout Report"))
          (is (.contains content "Building Rails API"))
          (is (.contains content "**Rails**"))
          (is (.contains content "**API**"))
          (is (.contains content "https://example.com/test")))

        (finally
          (when (.exists (io/file temp-file))
            (io/delete-file temp-file))))))

  (testing "Save empty alerts to markdown"
    (let [temp-file "test-resources/test-empty-alerts.md"]
      (try
        (storage/save-alerts! [] temp-file :markdown)

        (let [content (slurp temp-file)]
          (is (.contains content "# Alert Scout Report"))
          (is (.contains content "Total alerts: 0")))

        (finally
          (when (.exists (io/file temp-file))
            (io/delete-file temp-file)))))))

(deftest test-save-alerts-edn
  (testing "Save alerts to EDN format"
    (let [alerts [{:user-id "bob"
                   :rule-id "test-rule"
                   :item {:feed-id "blog"
                          :title "Test Article"
                          :link "https://example.com/test"
                          :published-at (Date.)}
                   :excerpts [{:text "Test excerpt"
                              :matched-terms ["test"]
                              :source :content}]}]
          temp-file "test-resources/test-alerts.edn"]
      (try
        ;; Save to file
        (storage/save-alerts! alerts temp-file :edn)

        ;; Verify file exists
        (is (.exists (io/file temp-file)))

        ;; Read and verify content
        (let [content (slurp temp-file)
              parsed (read-string content)]
          ;; Should be valid EDN
          (is (vector? parsed))
          (is (= 1 (count parsed)))
          (is (= "bob" (:user-id (first parsed))))
          (is (= "test-rule" (:rule-id (first parsed))))
          ;; Should include excerpts
          (is (vector? (:excerpts (first parsed))))
          (is (= 1 (count (:excerpts (first parsed)))))
          (is (= "Test excerpt" (:text (first (:excerpts (first parsed)))))))

        (finally
          (when (.exists (io/file temp-file))
            (io/delete-file temp-file))))))

  (testing "Save empty alerts to EDN"
    (let [temp-file "test-resources/test-empty-alerts.edn"]
      (try
        (storage/save-alerts! [] temp-file :edn)

        (let [content (slurp temp-file)
              parsed (read-string content)]
          (is (vector? parsed))
          (is (empty? parsed)))

        (finally
          (when (.exists (io/file temp-file))
            (io/delete-file temp-file)))))))

(deftest test-save-alerts-invalid-format
  (testing "Invalid format throws error"
    (let [alerts [{:user-id "alice"
                   :rule-id "test"
                   :item {:feed-id "hn" :title "Test"}}]
          temp-file "test-resources/test-alerts.txt"]
      (is (thrown-with-msg? Exception #"Unknown format"
                            (storage/save-alerts! alerts temp-file :invalid))))))

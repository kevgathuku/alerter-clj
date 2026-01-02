(ns alert-scout.core-test
  (:require [clojure.test :refer :all]
            [alert-scout.core :as core]
            [alert-scout.storage :as storage])
  (:import (java.util Date Calendar)))

;; Helper to create dates for testing
(defn days-ago [n]
  (let [cal (Calendar/getInstance)]
    (.add cal Calendar/DATE (- n))
    (.getTime cal)))

(deftest test-alerts-summary
  (testing "Summary with alerts (no color)"
    (let [alerts [{:rule-id "rule1"
                   :item {:feed-id "hn" :title "Test 1"}}
                  {:rule-id "rule2"
                   :item {:feed-id "blog" :title "Test 2"}}
                  {:rule-id "rule1"
                   :item {:feed-id "hn" :title "Test 3"}}]
          summary (core/alerts-summary alerts false)]
      (is (.contains summary "Total alerts: 3"))
      (is (.contains summary "rule1"))
      (is (.contains summary "rule2"))
      ;; Verify no ANSI color codes are present
      (is (not (.contains summary "\u001b[")))))

  (testing "Summary with alerts (with color)"
    (let [alerts [{:rule-id "rails-api"
                   :item {:feed-id "hn" :title "Test 1"}}]
          summary (core/alerts-summary alerts true)]
      (is (.contains summary "Total alerts: 1"))
      (is (.contains summary "rails-api"))
      ;; Verify ANSI color codes are present
      (is (.contains summary "\u001b["))))

  (testing "Summary with no alerts (no color)"
    (let [summary (core/alerts-summary [] false)]
      (is (.contains summary "No new alerts"))
      ;; Verify no ANSI color codes are present
      (is (not (.contains summary "\u001b[")))))

  (testing "Summary with no alerts (with color)"
    (let [summary (core/alerts-summary [] true)]
      (is (.contains summary "No new alerts"))
      ;; Verify ANSI color codes are present
      (is (.contains summary "\u001b["))))

  (testing "Summary defaults to no color when arity-1 called"
    (let [alerts [{:rule-id "test-rule"
                   :item {:feed-id "blog" :title "Test"}}]
          summary (core/alerts-summary alerts)]
      (is (.contains summary "Total alerts: 1"))
      (is (.contains summary "test-rule"))
      ;; Verify no ANSI color codes are present (default is no color)
      (is (not (.contains summary "\u001b["))))))

;; Integration-style test for process-feed logic
(deftest test-date-filtering-logic
  (testing "Items are filtered by date correctly"
    (with-redefs [storage/last-seen (constantly (days-ago 7))
                  storage/update-checkpoint! (fn [& _] nil)]
      (let [old-item {:feed-id "test"
                      :item-id "old"
                      :title "Old Article"
                      :link "https://example.com/old"
                      :published-at (days-ago 10)
                      :content nil}
            new-item {:feed-id "test"
                      :item-id "new"
                      :title "New Article"
                      :link "https://example.com/new"
                      :published-at (days-ago 3)
                      :content nil}
            items [old-item new-item]

            ;; Filter logic from process-feed
            last-seen (storage/last-seen "test")
            filtered (->> items
                          (filter #(when-let [ts (:published-at %)]
                                     (or (nil? last-seen)
                                         (.after ^Date ts last-seen))))
                          (sort-by :published-at))]

        ;; Only new item should pass filter
        (is (= 1 (count filtered)))
        (is (= "new" (:item-id (first filtered)))))))

  (testing "All items pass when no checkpoint exists"
    (with-redefs [storage/last-seen (constantly nil)]
      (let [item1 {:feed-id "test"
                   :item-id "1"
                   :title "Article 1"
                   :link "https://example.com/1"
                   :published-at (days-ago 10)
                   :content nil}
            item2 {:feed-id "test"
                   :item-id "2"
                   :title "Article 2"
                   :link "https://example.com/2"
                   :published-at (days-ago 3)
                   :content nil}
            items [item1 item2]

            last-seen (storage/last-seen "test")
            filtered (->> items
                          (filter #(when-let [ts (:published-at %)]
                                     (or (nil? last-seen)
                                         (.after ^Date ts last-seen)))))]

        ;; Both items should pass
        (is (= 2 (count filtered)))))))

(deftest test-sorting-logic
  (testing "Items are sorted by published date"
    (let [item1 {:published-at (days-ago 10)}
          item2 {:published-at (days-ago 5)}
          item3 {:published-at (days-ago 1)}
          items [item2 item3 item1]  ;; Unsorted
          sorted (sort-by :published-at items)]

      ;; Should be ordered oldest to newest
      (is (= item1 (first sorted)))
      (is (= item2 (second sorted)))
      (is (= item3 (last sorted))))))

(deftest test-deduplicate-alerts-by-url
  (testing "No duplicates - all alerts preserved"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "hn"
                          :title "Article 1"
                          :link "https://example.com/article-1"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "reddit"
                          :title "Article 2"
                          :link "https://example.com/article-2"}}
                  {:rule-id "rule-2"
                   :item {:feed-id "lobsters"
                          :title "Article 3"
                          :link "https://example.com/article-3"}}]
          result (core/deduplicate-alerts-by-url alerts)]
      (is (= 3 (count result)))
      (is (= (set alerts) (set result)))))

  (testing "Duplicates within same rule-id - keeps first occurrence"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "hn"
                          :title "HN: Clojure Article"
                          :link "https://example.com/clojure"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "reddit"
                          :title "Reddit: Clojure Article"
                          :link "https://example.com/clojure"}}  ;; Same URL
                  {:rule-id "rule-1"
                   :item {:feed-id "lobsters"
                          :title "Different Article"
                          :link "https://example.com/different"}}]
          result (core/deduplicate-alerts-by-url alerts)]
      ;; Should remove the Reddit duplicate
      (is (= 2 (count result)))
      ;; First occurrence (HN) should be kept
      (is (some #(= "hn" (get-in % [:item :feed-id])) result))
      ;; Second occurrence (Reddit) should be removed
      (is (not (some #(= "reddit" (get-in % [:item :feed-id])) result)))
      ;; Different article should be kept
      (is (some #(= "https://example.com/different" (get-in % [:item :link])) result))))

  (testing "Same URL across different rules - both preserved"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "hn"
                          :title "Article"
                          :link "https://example.com/article"}}
                  {:rule-id "rule-2"
                   :item {:feed-id "reddit"
                          :title "Same Article"
                          :link "https://example.com/article"}}]  ;; Same URL, different rule
          result (core/deduplicate-alerts-by-url alerts)]
      ;; Both should be kept (different rules)
      (is (= 2 (count result)))
      (is (some #(= "rule-1" (:rule-id %)) result))
      (is (some #(= "rule-2" (:rule-id %)) result))))

  (testing "Multiple duplicates of same URL - keeps first"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "hn"
                          :title "First"
                          :link "https://example.com/same"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "reddit"
                          :title "Second"
                          :link "https://example.com/same"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "lobsters"
                          :title "Third"
                          :link "https://example.com/same"}}]
          result (core/deduplicate-alerts-by-url alerts)]
      ;; Only first should remain
      (is (= 1 (count result)))
      (is (= "hn" (get-in (first result) [:item :feed-id])))))

  (testing "Empty input"
    (let [result (core/deduplicate-alerts-by-url [])]
      (is (empty? result))
      (is (vector? result))))

  (testing "Single alert"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "hn"
                          :title "Single"
                          :link "https://example.com/single"}}]
          result (core/deduplicate-alerts-by-url alerts)]
      (is (= 1 (count result)))
      (is (= alerts result))))

  (testing "Complex scenario - multiple rules with duplicates"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "hn"
                          :title "Article A"
                          :link "https://example.com/a"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "reddit"
                          :title "Article A (duplicate)"
                          :link "https://example.com/a"}}  ;; Duplicate in rule-1
                  {:rule-id "rule-1"
                   :item {:feed-id "lobsters"
                          :title "Article B"
                          :link "https://example.com/b"}}
                  {:rule-id "rule-2"
                   :item {:feed-id "hn"
                          :title "Article A in rule-2"
                          :link "https://example.com/a"}}  ;; Same URL, different rule
                  {:rule-id "rule-2"
                   :item {:feed-id "reddit"
                          :title "Article C"
                          :link "https://example.com/c"}}]
          result (core/deduplicate-alerts-by-url alerts)]
      ;; Should have 4 alerts: rule-1 has 2 unique, rule-2 has 2 unique
      (is (= 4 (count result)))
      ;; Verify rule-1 kept first occurrence of /a
      (let [rule-1-alerts (filter #(= "rule-1" (:rule-id %)) result)]
        (is (= 2 (count rule-1-alerts)))
        (is (some #(= "hn" (get-in % [:item :feed-id])) rule-1-alerts))
        (is (not (some #(= "reddit" (get-in % [:item :feed-id])) rule-1-alerts))))
      ;; Verify rule-2 has both alerts
      (let [rule-2-alerts (filter #(= "rule-2" (:rule-id %)) result)]
        (is (= 2 (count rule-2-alerts))))))

  (testing "Result is a vector"
    (let [alerts [{:rule-id "rule-1"
                   :item {:link "https://example.com/test"}}]
          result (core/deduplicate-alerts-by-url alerts)]
      (is (vector? result))))

  (testing "Order preservation within rule - keeps first occurrence"
    (let [alerts [{:rule-id "rule-1"
                   :item {:feed-id "feed-1"
                          :link "https://example.com/unique-1"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "feed-2"
                          :link "https://example.com/duplicate"}}  ;; First occurrence
                  {:rule-id "rule-1"
                   :item {:feed-id "feed-3"
                          :link "https://example.com/unique-2"}}
                  {:rule-id "rule-1"
                   :item {:feed-id "feed-4"
                          :link "https://example.com/duplicate"}}]  ;; Should be removed
          result (core/deduplicate-alerts-by-url alerts)]
      (is (= 3 (count result)))
      ;; First occurrence of duplicate should be from feed-2
      (let [dup-alert (first (filter #(= "https://example.com/duplicate"
                                         (get-in % [:item :link]))
                                     result))]
        (is (= "feed-2" (get-in dup-alert [:item :feed-id])))))))

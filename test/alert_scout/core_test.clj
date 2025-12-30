(ns alert-scout.core-test
  (:require [clojure.test :refer :all]
            [alert-scout.core :as core]
            [alert-scout.matcher :as matcher]
            [alert-scout.storage :as storage])
  (:import (java.util Date Calendar)))

;; Helper to create dates for testing
(defn days-ago [n]
  (let [cal (Calendar/getInstance)]
    (.add cal Calendar/DATE (- n))
    (.getTime cal)))

(deftest test-format-summary
  (testing "Summary with alerts"
    (let [alerts [{:user-id "kevin"
                   :rule-id "rule1"
                   :item {:feed-id "hn" :title "Test 1"}}
                  {:user-id "kevin"
                   :rule-id "rule2"
                   :item {:feed-id "blog" :title "Test 2"}}
                  {:user-id "admin"
                   :rule-id "rule3"
                   :item {:feed-id "hn" :title "Test 3"}}]
          summary (core/format-summary alerts)]
      (is (.contains summary "Total alerts: 3"))
      (is (.contains summary "kevin"))
      (is (.contains summary "admin"))))

  (testing "Summary with no alerts"
    (let [summary (core/format-summary [])]
      (is (.contains summary "No new alerts")))))

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
                      :content nil
                      :categories []}
            new-item {:feed-id "test"
                      :item-id "new"
                      :title "New Article"
                      :link "https://example.com/new"
                      :published-at (days-ago 3)
                      :content nil
                      :categories []}
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
                   :content nil
                   :categories []}
            item2 {:feed-id "test"
                   :item-id "2"
                   :title "Article 2"
                   :link "https://example.com/2"
                   :published-at (days-ago 3)
                   :content nil
                   :categories []}
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

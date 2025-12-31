(ns alert-scout.matcher-test
  (:require [clojure.test :refer :all]
            [alert-scout.matcher :as matcher]
            [alert-scout.schemas :as schemas])
  (:import (java.util Date)))

(deftest test-text
  (testing "Extract and normalize text from feed item"
    (is (= "hello world test content"
           (matcher/text {:title "Hello World"
                          :content "Test Content"})))

    (is (= "just title"
           (matcher/text {:title "Just Title"})))

    (is (= ""
           (matcher/text {:title nil :content nil})))))

(deftest test-contains-term?
  (testing "Case-insensitive term matching"
    (is (matcher/contains-term? "hello world" "hello"))
    (is (matcher/contains-term? "hello world" "WORLD"))
    (is (matcher/contains-term? "Rails API" "rails"))
    (is (not (matcher/contains-term? "hello" "world")))))

(deftest test-match-rule-must
  (testing "Rule with only 'must' clauses"
    (let [rule {:must ["rails" "api"]
                :should []
                :must-not []
                :min-should-match 0}
          item {:title "Building Rails API" :content ""}]
      (is (matcher/match-rule? rule item)))

    (let [rule {:must ["rails" "api"]
                :should []
                :must-not []
                :min-should-match 0}
          item {:title "Building Rails" :content ""}]
      (is (not (matcher/match-rule? rule item))))))

(deftest test-match-rule-must-not
  (testing "Rule with 'must-not' clauses"
    (let [rule {:must ["rails"]
                :should []
                :must-not ["test"]
                :min-should-match 0}
          item {:title "Rails Production Deploy" :content ""}]
      (is (matcher/match-rule? rule item)))

    (let [rule {:must ["rails"]
                :should []
                :must-not ["test"]
                :min-should-match 0}
          item {:title "Rails Testing Guide" :content ""}]
      (is (not (matcher/match-rule? rule item))))))

(deftest test-match-rule-should
  (testing "Rule with 'should' and 'min-should-match'"
    (let [rule {:must []
                :should ["docker" "kamal" "deploy"]
                :must-not []
                :min-should-match 2}
          item {:title "Deploy with Kamal and Docker" :content ""}]
      (is (matcher/match-rule? rule item)))

    ;; Only has 1 match (docker), needs 2
    (let [rule {:must []
                :should ["docker" "kamal" "rails"]
                :must-not []
                :min-should-match 2}
          item {:title "Building with Docker" :content ""}]
      (is (not (matcher/match-rule? rule item))))))

(deftest test-match-rule-combined
  (testing "Complex rule with must, should, and must-not"
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Rails Deploy with Docker" :content ""}]
      (is (matcher/match-rule? rule item)))

    ;; Missing 'must' term
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Deploy with Docker" :content ""}]
      (is (not (matcher/match-rule? rule item))))

    ;; Has 'must-not' term
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Rails Deploy Testing" :content ""}]
      (is (not (matcher/match-rule? rule item))))

    ;; Not enough 'should' matches
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Rails Tutorial" :content ""}]
      (is (not (matcher/match-rule? rule item))))))

(deftest test-match-item
  (testing "Generate alerts from matching rules"
    (let [rules-by-user {"kevin" [{:id "rails-api"
                                   :user-id "kevin"
                                   :must ["rails" "api"]
                                   :should []
                                   :must-not ["test"]
                                   :min-should-match 0}]
                         "admin" [{:id "admin-rule"
                                   :user-id "admin"
                                   :must ["admin"]
                                   :should []
                                   :must-not []
                                   :min-should-match 0}]}
          item {:feed-id "hn"
                :title "Building Rails API"
                :content "How to build a Rails API"}]

      (let [alerts (matcher/match-item rules-by-user item)]
        (is (= 1 (count alerts)))
        (is (= "kevin" (:user-id (first alerts))))
        (is (= "rails-api" (:rule-id (first alerts))))
        (is (= item (:item (first alerts)))))))

  (testing "No matches returns empty vector"
    (let [rules-by-user {"kevin" [{:id "python-rule"
                                   :user-id "kevin"
                                   :must ["python"]
                                   :should []
                                   :must-not []
                                   :min-should-match 0}]}
          item {:title "Building Rails API" :content ""}]

      (is (= [] (matcher/match-item rules-by-user item))))))

(deftest test-rules-by-user
  (testing "Group rules by user-id"
    (let [rules [{:id "rule1" :user-id "alice"}
                 {:id "rule2" :user-id "bob"}
                 {:id "rule3" :user-id "alice"}]
          grouped (matcher/rules-by-user rules)]

      (is (= 2 (count grouped)))
      (is (= 2 (count (get grouped "alice"))))
      (is (= 1 (count (get grouped "bob")))))))

;; --- Integration Tests (US1) ---

(deftest test-match-item-returns-alerts-with-excerpts
  (testing "match-item returns alerts with excerpts when terms match"
    (let [rules-by-user {"kevin" [{:id "rails-api"
                                   :user-id "kevin"
                                   :must ["rails" "api"]
                                   :should []
                                   :must-not []
                                   :min-should-match 0}]}
          item {:feed-id "hn"
                :item-id "123"
                :title "Building Rails API"
                :content "Learn how to build a Rails API for modern web applications with authentication and testing."
                :link "https://example.com/rails-api"
                :published-at (Date.)
                :categories ["programming"]}
          alerts (matcher/match-item rules-by-user item)]

      ;; Should have one alert
      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should have correct metadata
        (is (= "kevin" (:user-id alert)))
        (is (= "rails-api" (:rule-id alert)))
        (is (= item (:item alert)))

        ;; Should have excerpts
        (is (contains? alert :excerpts))
        (is (vector? (:excerpts alert)))
        (is (pos? (count (:excerpts alert))))

        ;; Excerpts should be valid according to schema
        (is (every? #(schemas/valid? schemas/Excerpt %) (:excerpts alert)))

        ;; Excerpts should have matched terms
        (is (every? #(seq (:matched-terms %)) (:excerpts alert)))

        ;; Excerpts should have source field
        (is (every? #(contains? % :source) (:excerpts alert)))
        (is (every? #(#{:title :content} (:source %)) (:excerpts alert))))))

  (testing "match-item includes both title and content excerpts when both match"
    (let [rules-by-user {"alice" [{:id "test-rule"
                                   :user-id "alice"
                                   :must ["rails"]
                                   :should []
                                   :must-not []
                                   :min-should-match 0}]}
          item {:feed-id "blog"
                :item-id "456"
                :title "Rails Development Best Practices"
                :content "Rails is a powerful framework. Many developers choose Rails for productivity."
                :link "https://example.com/rails-dev"
                :published-at (Date.)
                :categories ["web"]}
          alerts (matcher/match-item rules-by-user item)]

      (is (= 1 (count alerts)))

      (let [alert (first alerts)
            excerpts (:excerpts alert)
            sources (set (map :source excerpts))]
        ;; Should have excerpts from both sources
        (is (contains? sources :title))
        (is (contains? sources :content)))))

  (testing "match-item limits excerpts to 3 total"
    (let [rules-by-user {"bob" [{:id "many-matches"
                                 :user-id "bob"
                                 :must ["rails"]
                                 :should []
                                 :must-not []
                                 :min-should-match 0}]}
          ;; Content with many distant matches
          item {:feed-id "news"
                :item-id "789"
                :title "Rails Framework News"
                :content (str "Rails version 8 introduces major features for web development. "
                              "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do. "
                              "Rails continues to be popular among startups and enterprises. "
                              "Ut enim ad minim veniam quis nostrud exercitation ullamco. "
                              "Rails community releases new gems and tools regularly.")
                :link "https://example.com/rails-news"
                :published-at (Date.)
                :categories ["frameworks"]}
          alerts (matcher/match-item rules-by-user item)]

      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should never exceed 3 excerpts
        (is (<= (count (:excerpts alert)) 3))))))

(deftest test-match-item-handles-nil-content
  (testing "match-item handles nil content gracefully"
    (let [rules-by-user {"alice" [{:id "rails-rule"
                                   :user-id "alice"
                                   :must ["rails"]
                                   :should []
                                   :must-not []
                                   :min-should-match 0}]}
          item {:feed-id "hn"
                :item-id "nil-content"
                :title "Building Rails Applications"
                :content nil
                :link "https://example.com/rails"
                :published-at (Date.)
                :categories []}
          alerts (matcher/match-item rules-by-user item)]

      ;; Should still generate alert
      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should have excerpts from title only
        (is (contains? alert :excerpts))
        (is (vector? (:excerpts alert)))
        (is (pos? (count (:excerpts alert))))

        ;; All excerpts should be from title
        (is (every? #(= :title (:source %)) (:excerpts alert)))

        ;; Excerpts should still be valid
        (is (every? #(schemas/valid? schemas/Excerpt %) (:excerpts alert))))))

  (testing "match-item handles empty content gracefully"
    (let [rules-by-user {"bob" [{:id "api-rule"
                                 :user-id "bob"
                                 :must ["api"]
                                 :should []
                                 :must-not []
                                 :min-should-match 0}]}
          item {:feed-id "blog"
                :item-id "empty-content"
                :title "API Development Guide"
                :content ""
                :link "https://example.com/api"
                :published-at (Date.)
                :categories []}
          alerts (matcher/match-item rules-by-user item)]

      ;; Should still generate alert
      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should have excerpts from title only
        (is (pos? (count (:excerpts alert))))
        (is (every? #(= :title (:source %)) (:excerpts alert))))))

  (testing "match-item handles both nil title and content"
    (let [rules-by-user {"charlie" [{:id "test-rule"
                                     :user-id "charlie"
                                     :must ["rails"]
                                     :should []
                                     :must-not []
                                     :min-should-match 0}]}
          item {:feed-id "test"
                :item-id "nil-both"
                :title nil
                :content nil
                :link "https://example.com/test"
                :published-at (Date.)
                :categories []}
          alerts (matcher/match-item rules-by-user item)]

      ;; Should not match (no text to match against)
      (is (empty? alerts)))))

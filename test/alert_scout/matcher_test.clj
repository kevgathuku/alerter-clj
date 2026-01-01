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

(deftest test-apply-rule-must
  (testing "Rule with only 'must' clauses returns matched terms"
    (let [rule {:must ["rails" "api"]
                :should []
                :must-not []
                :min-should-match 0}
          item {:title "Building Rails API" :content ""}
          result (matcher/apply-rule rule item)]
      ;; Should return matched terms
      (is (some? result))
      (is (vector? result))
      (is (= #{"rails" "api"} (set result)))))

  (testing "Rule with missing 'must' term returns nil"
    (let [rule {:must ["rails" "api"]
                :should []
                :must-not []
                :min-should-match 0}
          item {:title "Building Rails" :content ""}]
      (is (nil? (matcher/apply-rule rule item))))))

(deftest test-apply-rule-must-not
  (testing "Rule with 'must-not' clauses returns matched terms when allowed"
    (let [rule {:must ["rails"]
                :should []
                :must-not ["test"]
                :min-should-match 0}
          item {:title "Rails Production Deploy" :content ""}
          result (matcher/apply-rule rule item)]
      (is (some? result))
      (is (= ["rails"] result))))

  (testing "Rule with 'must-not' term present returns nil"
    (let [rule {:must ["rails"]
                :should []
                :must-not ["test"]
                :min-should-match 0}
          item {:title "Rails Testing Guide" :content ""}]
      (is (nil? (matcher/apply-rule rule item))))))

(deftest test-apply-rule-should
  (testing "Rule with 'should' and 'min-should-match' returns matched terms"
    (let [rule {:must []
                :should ["docker" "kamal" "deploy"]
                :must-not []
                :min-should-match 2}
          item {:title "Deploy with Kamal and Docker" :content ""}
          result (matcher/apply-rule rule item)]
      (is (some? result))
      ;; Should have at least 2 of the should terms
      (is (>= (count result) 2))))

  (testing "Rule with insufficient 'should' matches returns nil"
    ;; Only has 1 match (docker), needs 2
    (let [rule {:must []
                :should ["docker" "kamal" "rails"]
                :must-not []
                :min-should-match 2}
          item {:title "Building with Docker" :content ""}]
      (is (nil? (matcher/apply-rule rule item))))))

(deftest test-apply-rule-combined
  (testing "Complex rule with must, should, and must-not returns matched terms"
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Rails Deploy with Docker" :content ""}
          result (matcher/apply-rule rule item)]
      (is (some? result))
      ;; Should have must + matching should terms
      (is (every? (set result) (:must rule)))
      (is (every? (set result) (-> rule :should set (disj "kamal"))))))

  (testing "Rule with missing 'must' term returns nil"
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Deploy with Docker" :content ""}]
      (is (nil? (matcher/apply-rule rule item)))))

  (testing "Rule with 'must-not' term present returns nil"
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Rails Deploy Testing" :content ""}]
      (is (nil? (matcher/apply-rule rule item)))))

  (testing "Rule with insufficient 'should' matches returns nil"
    (let [rule {:must ["rails"]
                :should ["docker" "kamal" "deploy"]
                :must-not ["test"]
                :min-should-match 1}
          item {:title "Rails Tutorial" :content ""}]
      (is (nil? (matcher/apply-rule rule item))))))

(deftest test-match-item
  (testing "Generate alerts from matching rules"
    (let [rules [{:id "rails-api"
                  :must ["rails" "api"]
                  :should []
                  :must-not ["test"]
                  :min-should-match 0}
                 {:id "admin-rule"
                  :must ["admin"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:feed-id "hn"
                :title "Building Rails API"
                :content "How to build a Rails API"}
          alerts (matcher/match-item rules item)]

      ;; Only rails-api rule should match
      (is (= 1 (count alerts)))
      (is (= "rails-api" (:rule-id (first alerts))))
      (is (= item (:item (first alerts))))
      ;; Should have excerpts
      (is (contains? (first alerts) :excerpts))))

  (testing "No matches returns empty sequence"
    (let [rules [{:id "python-rule"
                  :must ["python"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:title "Building Rails API" :content ""}
          alerts (matcher/match-item rules item)]

      (is (empty? alerts)))))

;; --- Integration Tests (US1) ---

(deftest test-match-item-returns-alerts-with-excerpts
  (testing "match-item returns alerts with excerpts when terms match"
    (let [rules [{:id "rails-api"
                  :must ["rails" "api"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:feed-id "hn"
                :item-id "123"
                :title "Building Rails API"
                :content "Learn how to build a Rails API for modern web applications with authentication and testing."
                :link "https://example.com/rails-api"
                :published-at (Date.)
                :categories ["programming"]}
          alerts (matcher/match-item rules item)]

      ;; Should have one alert
      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should have correct metadata
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
    (let [rules [{:id "test-rule"
                  :must ["rails"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:feed-id "blog"
                :item-id "456"
                :title "Rails Development Best Practices"
                :content "Rails is a powerful framework. Many developers choose Rails for productivity."
                :link "https://example.com/rails-dev"
                :published-at (Date.)
                :categories ["web"]}
          alerts (matcher/match-item rules item)]

      (is (= 1 (count alerts)))

      (let [alert (first alerts)
            excerpts (:excerpts alert)
            sources (set (map :source excerpts))]
        ;; Should have excerpts from both sources
        (is (contains? sources :title))
        (is (contains? sources :content)))))

  (testing "match-item limits excerpts to 3 total"
    (let [rules [{:id "many-matches"
                  :must ["rails"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
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
          alerts (matcher/match-item rules item)]

      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should never exceed 3 excerpts
        (is (<= (count (:excerpts alert)) 3))))))

(deftest test-match-item-handles-nil-content
  (testing "match-item handles nil content gracefully"
    (let [rules [{:id "rails-rule"
                  :must ["rails"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:feed-id "hn"
                :item-id "nil-content"
                :title "Building Rails Applications"
                :content nil
                :link "https://example.com/rails"
                :published-at (Date.)
                :categories []}
          alerts (matcher/match-item rules item)]

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
    (let [rules [{:id "api-rule"
                  :must ["api"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:feed-id "blog"
                :item-id "empty-content"
                :title "API Development Guide"
                :content ""
                :link "https://example.com/api"
                :published-at (Date.)
                :categories []}
          alerts (matcher/match-item rules item)]

      ;; Should still generate alert
      (is (= 1 (count alerts)))

      (let [alert (first alerts)]
        ;; Should have excerpts from title only
        (is (pos? (count (:excerpts alert))))
        (is (every? #(= :title (:source %)) (:excerpts alert))))))

  (testing "match-item handles both nil title and content"
    (let [rules [{:id "test-rule"
                  :must ["rails"]
                  :should []
                  :must-not []
                  :min-should-match 0}]
          item {:feed-id "test"
                :item-id "nil-both"
                :title nil
                :content nil
                :link "https://example.com/test"
                :published-at (Date.)
                :categories []}
          alerts (matcher/match-item rules item)]

      ;; Should not match (no text to match against)
      (is (empty? alerts)))))

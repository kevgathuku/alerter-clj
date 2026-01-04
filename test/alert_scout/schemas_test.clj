(ns alert-scout.schemas-test
  (:require [clojure.test :refer :all]
            [alert-scout.schemas :as schemas])
  (:import (java.util Date)))

;; --- Test Data Helpers ---

(defn valid-feed-item []
  {:feed-id "test"
   :item-id "item-123"
   :title "Test Title"
   :link "https://example.com"
   :published-at (Date.)
   :content "Test content"})

;; --- Excerpt Schema Tests (T006) ---

(deftest test-excerpt-schema-valid
  (testing "Valid excerpt with all required fields"
    (is (schemas/valid? schemas/Excerpt
                        {:text "...Rails API..."
                         :matched-terms ["rails" "api"]
                         :source :content})))

  (testing "Valid excerpt from title"
    (is (schemas/valid? schemas/Excerpt
                        {:text "Building Rails API"
                         :matched-terms ["rails" "api"]
                         :source :title})))

  (testing "Valid excerpt with single term"
    (is (schemas/valid? schemas/Excerpt
                        {:text "Sample text with term"
                         :matched-terms ["term"]
                         :source :content}))))

(deftest test-excerpt-schema-invalid-text
  (testing "Invalid excerpt - empty text"
    (is (not (schemas/valid? schemas/Excerpt
                             {:text ""
                              :matched-terms ["rails"]
                              :source :content}))))

  (testing "Invalid excerpt - missing text"
    (is (not (schemas/valid? schemas/Excerpt
                             {:matched-terms ["rails"]
                              :source :content})))))

(deftest test-excerpt-schema-invalid-source
  (testing "Invalid excerpt - wrong source enum value"
    (is (not (schemas/valid? schemas/Excerpt
                             {:text "text"
                              :matched-terms ["rails"]
                              :source :body}))))

  (testing "Invalid excerpt - source as string instead of keyword"
    (is (not (schemas/valid? schemas/Excerpt
                             {:text "text"
                              :matched-terms ["rails"]
                              :source "content"})))))

(deftest test-excerpt-schema-invalid-matched-terms
  (testing "Invalid excerpt - empty matched-terms vector"
    (is (not (schemas/valid? schemas/Excerpt
                             {:text "text"
                              :matched-terms []
                              :source :content}))))

  (testing "Invalid excerpt - matched-terms with empty string"
    (is (not (schemas/valid? schemas/Excerpt
                             {:text "text"
                              :matched-terms [""]
                              :source :content}))))

  (testing "Invalid excerpt - missing matched-terms"
    (is (not (schemas/valid? schemas/Excerpt
                             {:text "text"
                              :source :content})))))

(deftest test-excerpt-schema-explain
  (testing "Explain errors for invalid excerpt"
    (let [errors (schemas/explain schemas/Excerpt
                                  {:text ""
                                   :matched-terms []
                                   :source :body})]
      (is (map? errors))
      (is (contains? errors :text))
      (is (contains? errors :matched-terms))
      (is (contains? errors :source)))))

;; --- Enhanced Alert Schema Tests (T007) ---

(deftest test-alert-with-excerpts
  (testing "Valid alert with excerpts"
    (is (schemas/valid? schemas/Alert
                        {:rule-id "test-rule"
                         :item (valid-feed-item)
                         :excerpts [{:text "...Rails API..."
                                     :matched-terms ["rails" "api"]
                                     :source :content}]})))

  (testing "Valid alert with multiple excerpts (up to 3)"
    (is (schemas/valid? schemas/Alert
                        {:rule-id "test-rule"
                         :item (valid-feed-item)
                         :excerpts [{:text "excerpt1"
                                     :matched-terms ["rails"]
                                     :source :title}
                                    {:text "excerpt2"
                                     :matched-terms ["api"]
                                     :source :content}
                                    {:text "excerpt3"
                                     :matched-terms ["rails"]
                                     :source :content}]})))

  (testing "Valid alert with exactly 3 excerpts (max limit)"
    (let [three-excerpts (repeat 3 {:text "excerpt"
                                    :matched-terms ["term"]
                                    :source :content})]
      (is (schemas/valid? schemas/Alert
                          {:rule-id "test-rule"
                           :item (valid-feed-item)
                           :excerpts (vec three-excerpts)})))))

(deftest test-alert-without-excerpts-backwards-compatible
  (testing "Valid alert without excerpts field (backwards compatible)"
    (is (schemas/valid? schemas/Alert
                        {:rule-id "test-rule"
                         :item (valid-feed-item)})))

  (testing "Valid alert with empty excerpts vector"
    (is (schemas/valid? schemas/Alert
                        {:rule-id "test-rule"
                         :item (valid-feed-item)
                         :excerpts []}))))

(deftest test-alert-with-invalid-excerpts
  (testing "Invalid alert - too many excerpts (>3)"
    (let [four-excerpts (repeat 4 {:text "excerpt"
                                   :matched-terms ["term"]
                                   :source :content})]
      (is (not (schemas/valid? schemas/Alert
                               {:rule-id "test-rule"
                                :item (valid-feed-item)
                                :excerpts (vec four-excerpts)})))))

  (testing "Invalid alert - excerpts contain invalid excerpt"
    (is (not (schemas/valid? schemas/Alert
                             {:rule-id "test-rule"
                              :item (valid-feed-item)
                              :excerpts [{:text ""
                                          :matched-terms []
                                          :source :invalid}]})))))

(deftest test-alert-validation-function
  (testing "validate-alert with valid data"
    (let [valid-alert {:rule-id "test-rule"
                       :item (valid-feed-item)
                       :excerpts [{:text "excerpt"
                                   :matched-terms ["term"]
                                   :source :content}]}]
      (is (= valid-alert (schemas/validate-alert valid-alert)))))

  (testing "validate-alert throws on invalid data"
    (is (thrown? Exception
                 (schemas/validate-alert
                  {:rule-id ""
                   :item (valid-feed-item)})))))

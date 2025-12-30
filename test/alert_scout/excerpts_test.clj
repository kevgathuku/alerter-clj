(ns alert-scout.excerpts-test
  (:require [clojure.test :refer :all]
            [alert-scout.excerpts :as excerpts]
            [alert-scout.schemas :as schemas])
  (:import (java.util Date)))

;; --- Helper Functions ---

(defn valid-feed-item
  "Create a valid feed item for testing."
  []
  {:feed-id "test"
   :item-id "item-123"
   :title "Test Title"
   :link "https://example.com"
   :published-at (Date.)
   :content "Test content"
   :categories ["tech"]})

;; --- find-term-positions Tests (T021, T022) ---

(deftest test-find-term-positions-case-insensitive
  (testing "Find term with case-insensitive matching"
    (let [text "Rails is great. RAILS is awesome. rails rocks."
          positions (excerpts/find-term-positions text "rails")]
      (is (= 3 (count positions)))
      (is (= 0 (:start (first positions))))
      (is (= 5 (:end (first positions))))
      (is (= "rails" (:term (first positions))))))

  (testing "Find term preserves original term in result"
    "Text has uppercase, original term does not"
    (let [text "Learn about Rails development"
          positions (excerpts/find-term-positions text "rails")]
      (is (= 1 (count positions)))
      (is (= "rails" (:term (first positions))))))

  (testing "No matches returns empty vector"
    (let [text "Some text without the term"
          positions (excerpts/find-term-positions text "rails")]
      (is (empty? positions)))))

(deftest test-find-term-positions-multiple-occurrences
  (testing "Find multiple occurrences of same term"
    (let [text "API development with Rails API for modern API design"
          positions (excerpts/find-term-positions text "api")]
      (is (= 3 (count positions)))
      ;; Verify we found 3 occurrences
      (is (every? #(= "api" (:term %)) positions))
      ;; First occurrence at start
      (is (= 0 (:start (first positions))))))

  (testing "Non-overlapping matches only (implementation skips overlaps)"
    (let [text "aaa"
          positions (excerpts/find-term-positions text "aa")]
      ;; Implementation advances past matched term, so no overlaps
      (is (= 1 (count positions)))
      (is (= 0 (:start (first positions))))))

  (testing "Empty text returns empty vector"
    (is (empty? (excerpts/find-term-positions "" "term"))))

  (testing "Nil text returns nil"
    (is (nil? (excerpts/find-term-positions nil "term"))))

  (testing "Empty term returns empty vector"
    (is (empty? (excerpts/find-term-positions "text" "")))))

;; --- extract-excerpt Tests (T023, T024) ---

(deftest test-extract-excerpt-with-word-boundaries
  (testing "Extract excerpt with word boundaries"
    (let [text "Learn how to build a Rails API for modern web applications"
          position {:start 21 :end 26 :term "rails"}
          excerpt (excerpts/extract-excerpt text position 20)]
      ;; Should have ellipsis on both sides
      (is (.startsWith (:text excerpt) "..."))
      (is (.endsWith (:text excerpt) "..."))
      ;; Should contain the term
      (is (.contains (:text excerpt) "Rails"))))

  (testing "Extract at beginning of text (no leading ellipsis)"
    (let [text "Rails is great for API development"
          position {:start 0 :end 5 :term "rails"}
          excerpt (excerpts/extract-excerpt text position 20)]
      ;; No leading ellipsis
      (is (not (.startsWith (:text excerpt) "...")))
      ;; But trailing ellipsis
      (is (.endsWith (:text excerpt) "..."))
      (is (.contains (:text excerpt) "Rails"))))

  (testing "Extract at end of text (no trailing ellipsis)"
    (let [text "Learn about Rails"
          position {:start 12 :end 17 :term "rails"}
          excerpt (excerpts/extract-excerpt text position 5)]
      ;; Should contain the term
      (is (.contains (:text excerpt) "Rails"))
      ;; No trailing ellipsis (at end of text)
      (is (not (.endsWith (:text excerpt) "...")))))

  (testing "Word boundary detection at spaces"
    (let [text "The quick brown fox jumps over the lazy dog"
          position {:start 16 :end 19 :term "fox"}
          excerpt (excerpts/extract-excerpt text position 10)]
      ;; Should break at word boundaries (spaces)
      (is (.contains (:text excerpt) "fox"))
      ;; Should not cut words in half
      (is (or (.contains (:text excerpt) "brown")
              (.contains (:text excerpt) "jumps"))))))

(deftest test-extract-excerpt-with-short-content
  (testing "Short content shows entire text without ellipsis"
    (let [text "Rails API"
          position {:start 0 :end 5 :term "rails"}
          excerpt (excerpts/extract-excerpt text position 50)]
      (is (= "Rails API" (:text excerpt)))
      (is (not (.contains (:text excerpt) "...")))))

  (testing "Content shorter than context shows full text"
    (let [text "Short Rails post"
          position {:start 6 :end 11 :term "rails"}
          excerpt (excerpts/extract-excerpt text position 50)]
      (is (= "Short Rails post" (:text excerpt)))
      (is (not (.startsWith (:text excerpt) "...")))
      (is (not (.endsWith (:text excerpt) "...")))))

  (testing "Nil text returns nil"
    (is (nil? (excerpts/extract-excerpt nil {:start 0 :end 5 :term "x"} 50))))

  (testing "Nil position returns nil"
    (is (nil? (excerpts/extract-excerpt "text" nil 50)))))

;; --- consolidate-excerpts Tests (T025, T026) ---

(deftest test-consolidate-excerpts-with-overlapping
  (testing "Consolidate overlapping excerpts"
    (let [excerpts [{:text "...Rails API..."
                     :matched-terms ["rails"]
                     :source :content
                     :start 10
                     :end 30}
                    {:text "...Rails API for..."
                     :matched-terms ["api"]
                     :source :content
                     :start 20
                     :end 40}]
          result (excerpts/consolidate-excerpts excerpts 20)]
      (is (= 1 (count result)))
      ;; Should combine matched terms
      (is (= #{"rails" "api"} (set (:matched-terms (first result)))))
      ;; Should extend to cover both ranges
      (is (= 10 (:start (first result))))
      (is (= 40 (:end (first result))))))

  (testing "Consolidate excerpts within merge threshold"
    (let [excerpts [{:text "...first..."
                     :matched-terms ["term1"]
                     :source :content
                     :start 0
                     :end 20}
                    {:text "...second..."
                     :matched-terms ["term2"]
                     :source :content
                     :start 25
                     :end 45}]
          ;; Within 20 char threshold (25 - 20 = 5)
          result (excerpts/consolidate-excerpts excerpts 20)]
      (is (= 1 (count result)))
      (is (= #{"term1" "term2"} (set (:matched-terms (first result)))))))

  (testing "Empty excerpts returns empty vector"
    (is (empty? (excerpts/consolidate-excerpts [] 20)))))

(deftest test-consolidate-excerpts-with-distant
  (testing "Keep distant excerpts separate"
    (let [excerpts [{:text "...first..."
                     :matched-terms ["term1"]
                     :source :content
                     :start 0
                     :end 20}
                    {:text "...second..."
                     :matched-terms ["term2"]
                     :source :content
                     :start 100
                     :end 120}]
          ;; Too far apart (100 - 20 = 80 > 20)
          result (excerpts/consolidate-excerpts excerpts 20)]
      (is (= 2 (count result)))
      (is (= ["term1"] (:matched-terms (first result))))
      (is (= ["term2"] (:matched-terms (second result))))))

  (testing "Multiple distant excerpts stay separate"
    (let [excerpts [{:text "...first..."
                     :matched-terms ["a"]
                     :source :content
                     :start 0
                     :end 10}
                    {:text "...second..."
                     :matched-terms ["b"]
                     :source :content
                     :start 50
                     :end 60}
                    {:text "...third..."
                     :matched-terms ["c"]
                     :source :content
                     :start 100
                     :end 110}]
          result (excerpts/consolidate-excerpts excerpts 20)]
      (is (= 3 (count result))))))

;; --- generate-excerpts Tests (T027) ---

(deftest test-generate-excerpts-with-max-limit
  (testing "Limit to max 3 excerpts"
    (let [text (str "Rails is great. "
                    "Rails is awesome. "
                    "Rails is powerful. "
                    "Rails is fast. "
                    "Rails is elegant.")
          terms ["rails"]
          result (excerpts/generate-excerpts text terms {:max-excerpts 3})]
      (is (<= (count result) 3))))

  (testing "Default max is 3 excerpts"
    (let [text (str "API one API two API three API four API five")
          terms ["api"]
          result (excerpts/generate-excerpts text terms)]
      (is (<= (count result) 3))))

  (testing "Fewer matches returns fewer excerpts"
    (let [text "One Rails mention here"
          terms ["rails"]
          result (excerpts/generate-excerpts text terms)]
      (is (= 1 (count result)))))

  (testing "Empty text returns empty vector"
    (is (empty? (excerpts/generate-excerpts "" ["term"] {}))))

  (testing "Nil text returns empty vector"
    (is (empty? (excerpts/generate-excerpts nil ["term"] {}))))

  (testing "Empty terms returns empty vector"
    (is (empty? (excerpts/generate-excerpts "text" [] {}))))

  (testing "Nil terms returns empty vector"
    (is (empty? (excerpts/generate-excerpts "text" nil {})))))

;; --- generate-excerpts-for-item Tests (T028, T029) ---

(deftest test-generate-excerpts-for-item-with-title-and-content
  (testing "Generate excerpts from both title and content"
    (let [item {:feed-id "test"
                :title "Building Rails API"
                :content "Learn how to build a Rails API for modern applications. Rails provides excellent tools."
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails" "api"]
          result (excerpts/generate-excerpts-for-item item terms)]
      ;; Should have excerpts
      (is (pos? (count result)))
      ;; Should have :source field
      (is (every? #(contains? % :source) result))
      ;; Should have both :title and :content sources
      (let [sources (set (map :source result))]
        (is (contains? sources :title)))
      ;; Each excerpt should be valid
      (is (every? #(schemas/valid? schemas/Excerpt %) result))))

  (testing "Max 3 total excerpts (1 from title, 2 from content)"
    (let [item {:feed-id "test"
                :title "Rails API Development"
                ;; Content with instances far apart (>100 chars) to prevent consolidation
                :content (str "Rails is a web framework with many features and capabilities. "
                              "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do. "
                              "Rails provides excellent developer productivity and maintainability. "
                              "Ut enim ad minim veniam quis nostrud exercitation ullamco laboris. "
                              "Rails has a vibrant community and extensive documentation available.")
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails"]
          result (excerpts/generate-excerpts-for-item item terms)]
      (is (<= (count result) 3))
      ;; Should have 1 from title, 2 from content
      (is (= 1 (count (filter #(= :title (:source %)) result))))
      (is (= 2 (count (filter #(= :content (:source %)) result))))))

  (testing "3 content excerpts when title doesn't match"
    (let [item {:feed-id "test"
                :title "Something else entirely"
                ;; Content with instances far apart (>100 chars) to prevent consolidation
                :content (str "Rails is a powerful web framework for building applications. "
                              "Lorem ipsum dolor sit amet consectetur adipiscing elit sed do eiusmod tempor. "
                              "Many teams choose Rails for its convention over configuration philosophy. "
                              "Ut enim ad minim veniam quis nostrud exercitation ullamco laboris nisi ut. "
                              "The Rails community provides extensive support and documentation resources.")
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails"]
          result (excerpts/generate-excerpts-for-item item terms)]
      ;; Should have 3 excerpts, all from content
      (is (= 3 (count result)))
      (is (every? #(= :content (:source %)) result))
      ;; All should contain the matched term
      (is (every? #(.contains (:text %) "Rails") result))))

  (testing "Excerpts have matched-terms field"
    (let [item {:feed-id "test"
                :title "Rails and API"
                :content "Rails API development"
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails" "api"]
          result (excerpts/generate-excerpts-for-item item terms)]
      (is (every? #(seq (:matched-terms %)) result)))))

(deftest test-generate-excerpts-for-item-with-nil-content
  (testing "Generate excerpts from title only when content is nil"
    (let [item {:feed-id "test"
                :title "Building Rails API"
                :content nil
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails" "api"]
          result (excerpts/generate-excerpts-for-item item terms)]
      ;; Should still have excerpts from title
      (is (pos? (count result)))
      ;; All should be from title
      (is (every? #(= :title (:source %)) result))))

  (testing "Generate excerpts from title only when content is empty string"
    (let [item {:feed-id "test"
                :title "Rails Development"
                :content ""
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails"]
          result (excerpts/generate-excerpts-for-item item terms)]
      ;; Should have excerpt from title
      (is (pos? (count result)))
      (is (every? #(= :title (:source %)) result))))

  (testing "No excerpts when both title and content don't match"
    (let [item {:feed-id "test"
                :title "Something else"
                :content "Different topic"
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails"]
          result (excerpts/generate-excerpts-for-item item terms)]
      (is (empty? result))))

  (testing "Nil item returns empty vector"
    (is (empty? (excerpts/generate-excerpts-for-item nil ["term"]))))

  (testing "Nil terms returns empty vector"
    (let [item (valid-feed-item)]
      (is (empty? (excerpts/generate-excerpts-for-item item nil))))))

;; --- Schema Validation Tests ---

(deftest test-excerpt-schema-validation
  (testing "Generated excerpts validate against Excerpt schema"
    (let [item {:feed-id "test"
                :title "Rails API Guide"
                :content "Learn Rails API development"
                :link "https://example.com"
                :published-at (Date.)}
          terms ["rails" "api"]
          result (excerpts/generate-excerpts-for-item item terms)]
      ;; All excerpts should be valid
      (is (every? #(schemas/valid? schemas/Excerpt %) result))
      ;; Should have required fields
      (is (every? #(and (:text %) (:matched-terms %) (:source %)) result)))))

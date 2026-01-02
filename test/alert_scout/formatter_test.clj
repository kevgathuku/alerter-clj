(ns alert-scout.formatter-test
  (:require [clojure.test :refer :all]
            [alert-scout.formatter :as formatter])
  (:import (java.util Date)))

;; --- Colorize Tests ---

(deftest test-colorize
  (testing "ANSI color codes are applied"
    (let [result (formatter/colorize :green "test")]
      (is (.contains result "test"))
      (is (.contains result "\u001b[32m"))   ;; Green code
      (is (.contains result "\u001b[0m")))))  ;; Reset code

;; --- Highlight Terms Tests (T031) ---

(deftest test-highlight-terms-terminal-with-ansi-colors
  (testing "Highlight single term with ANSI colors"
    (let [text "Learn how to build a Rails API"
          terms ["rails"]
          result (formatter/highlight-terms-terminal text terms)]
      (is (.contains result "Rails"))
      (is (.contains result "\u001b[1m"))    ;; Bold code
      (is (.contains result "\u001b[33m")))  ;; Yellow code
    )

  (testing "Highlight multiple terms with ANSI colors"
    (let [text "Learn how to build a Rails API for modern applications"
          terms ["rails" "api"]
          result (formatter/highlight-terms-terminal text terms)]
      (is (.contains result "Rails"))
      (is (.contains result "API"))
      ;; Both terms should be highlighted
      (is (< (.indexOf result "\u001b[1m") (.indexOf result "Rails")))
      (is (< (.indexOf result "\u001b[1m") (.indexOf result "API")))))

  (testing "Highlight is case-insensitive"
    (let [text "RAILS rails Rails"
          terms ["rails"]
          result (formatter/highlight-terms-terminal text terms)]
      ;; All variations should be highlighted
      (is (.contains result "\u001b[1m"))))

  (testing "No highlighting when no terms provided"
    (let [text "Some text"
          result (formatter/highlight-terms-terminal text [])]
      (is (= text result))))

  (testing "No highlighting when text is nil"
    (let [result (formatter/highlight-terms-terminal nil ["term"])]
      (is (nil? result)))))

(deftest test-highlight-terms-markdown
  (testing "Highlight single term with markdown bold"
    (let [text "Learn how to build a Rails API"
          terms ["rails"]
          result (formatter/highlight-terms-markdown text terms)]
      (is (.contains result "**Rails**"))))

  (testing "Highlight multiple terms with markdown bold"
    (let [text "Learn how to build a Rails API for modern applications"
          terms ["rails" "api"]
          result (formatter/highlight-terms-markdown text terms)]
      (is (.contains result "**Rails**"))
      (is (.contains result "**API**"))))

  (testing "Highlight preserves original case"
    (let [text "RAILS rails Rails"
          terms ["rails"]
          result (formatter/highlight-terms-markdown text terms)]
      (is (.contains result "**RAILS**"))
      (is (.contains result "**rails**"))
      (is (.contains result "**Rails**"))))

  (testing "No highlighting when no terms provided"
    (let [text "Some text"
          result (formatter/highlight-terms-markdown text [])]
      (is (= text result)))))

;; --- Format Alert Tests (T032) ---

(deftest test-format-alert-without-excerpts
  (testing "Alert formatting includes all key information without excerpts"
    (let [alert {:rule-id "rails-api"
                 :item {:feed-id "hn"
                        :title "Building Rails API"
                        :link "https://example.com/article"
                        :published-at (Date.)}}
          formatted (formatter/format-alert alert)]
      (is (.contains formatted "rails-api"))
      (is (.contains formatted "hn"))
      (is (.contains formatted "Building Rails API"))
      (is (.contains formatted "https://example.com/article")))))

(deftest test-format-alert-with-excerpts
  (testing "Alert formatting includes excerpts with highlighting"
    (let [alert {:rule-id "rails-api"
                 :item {:feed-id "hn"
                        :title "Building Rails API"
                        :link "https://example.com/article"
                        :published-at (Date.)}
                 :excerpts [{:text "Building Rails API"
                             :matched-terms ["rails" "api"]
                             :source :title}
                            {:text "Learn how to build a Rails API for modern..."
                             :matched-terms ["rails" "api"]
                             :source :content}]}
          formatted (formatter/format-alert alert)]
      ;; Should contain alert metadata
      (is (.contains formatted "rails-api"))
      ;; Should contain source labels
      (is (.contains formatted "[Title]"))
      (is (.contains formatted "[Content]"))
      ;; Should contain excerpt text
      (is (.contains formatted "Building Rails API"))
      (is (.contains formatted "Learn how to build")))))

(deftest test-format-alert-with-empty-excerpts
  (testing "Alert formatting handles empty excerpts gracefully"
    (let [alert {:rule-id "test-rule"
                 :item {:feed-id "blog"
                        :title "Test Article"
                        :link "https://example.com/test"
                        :published-at (Date.)}
                 :excerpts []}
          formatted (formatter/format-alert alert)]
      ;; Should still show alert metadata
      (is (.contains formatted "test-rule"))
      ;; Should not have excerpt sections
      (is (not (.contains formatted "[Title]")))
      (is (not (.contains formatted "[Content]"))))))

;; --- Export Tests ---

(deftest test-alerts-to-markdown-without-excerpts
  (testing "Markdown export contains required elements without excerpts"
    (let [alerts [{:rule-id "rails-api"
                   :item {:feed-id "hn"
                          :title "Test Article"
                          :link "https://example.com/test"
                          :published-at (Date.)}}]
          markdown (formatter/alerts->markdown alerts)]
      (is (.contains markdown "# Alert Scout Report"))
      (is (.contains markdown "Total alerts: 1"))
      (is (.contains markdown "## Test Article"))
      (is (.contains markdown "**Feed**: hn"))
      (is (.contains markdown "**Rule**: rails-api"))
      (is (.contains markdown "https://example.com/test")))))

(deftest test-alerts-to-markdown-with-excerpts
  (testing "Markdown export includes excerpts with bold formatting"
    (let [alerts [{:rule-id "rails-api"
                   :item {:feed-id "hn"
                          :title "Building Rails API"
                          :link "https://example.com/test"
                          :published-at (Date.)}
                   :excerpts [{:text "Building Rails API"
                               :matched-terms ["rails" "api"]
                               :source :title}
                              {:text "Learn how to build a Rails API..."
                               :matched-terms ["rails" "api"]
                               :source :content}]}]
          markdown (formatter/alerts->markdown alerts)]
      (is (.contains markdown "# Alert Scout Report"))
      (is (.contains markdown "## Building Rails API"))
      (is (.contains markdown "**Matched Content:**"))
      (is (.contains markdown "[Title]"))
      (is (.contains markdown "[Content]"))
      ;; Should have markdown bold highlighting
      (is (.contains markdown "**Rails**"))
      (is (.contains markdown "**API**")))))

(deftest test-alerts-to-edn-without-excerpts
  (testing "EDN export is valid and contains data without excerpts"
    (let [alerts [{:rule-id "rails-api"
                   :item {:feed-id "hn"
                          :title "Test Article"
                          :link "https://example.com/test"
                          :published-at (Date.)}}]
          edn-str (pr-str alerts)
          parsed (read-string edn-str)]
      (is (vector? parsed))
      (is (= 1 (count parsed)))
      (is (= "rails-api" (:rule-id (first parsed))))
      (is (= "hn" (get-in (first parsed) [:item :feed-id]))))))

(deftest test-alerts-to-edn-with-excerpts
  (testing "EDN export includes excerpts as structured data"
    (let [alerts [{:rule-id "rails-api"
                   :item {:feed-id "hn"
                          :title "Building Rails API"
                          :link "https://example.com/test"
                          :published-at (Date.)}
                   :excerpts [{:text "Building Rails API"
                               :matched-terms ["rails" "api"]
                               :source :title}]}]
          edn-str (pr-str alerts)
          parsed (read-string edn-str)]
      (is (vector? parsed))
      (is (= 1 (count parsed)))
      (is (= "rails-api" (:rule-id (first parsed))))
      ;; Should include excerpts
      (is (vector? (:excerpts (first parsed))))
      (is (= 1 (count (:excerpts (first parsed)))))
      (is (= "Building Rails API" (:text (first (:excerpts (first parsed)))))))))

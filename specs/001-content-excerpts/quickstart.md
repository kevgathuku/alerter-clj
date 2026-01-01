# Quickstart: Content Excerpts in Alerts

**Feature**: Content Excerpts in Alerts
**Phase**: 1 (Design & Contracts)
**Date**: 2025-12-30

## Overview

This guide demonstrates how to use the content excerpts feature via the REPL, following the project's REPL-driven development workflow.

## Prerequisites

```bash
# Start REPL
lein repl
```

## Basic Usage

### 1. Generate Alerts with Excerpts (Default Behavior)

```clojure
;; Load the core namespace
(require '[alert-scout.core :as core])

;; Run feed processing (uses data/feeds.edn)
(def result (core/run-once))

;; Inspect results
(:items-processed result)  ; => 15
(count (:alerts result))   ; => 3

;; Look at first alert
(first (:alerts result))
;; => {:user-id "alice"
;;     :rule-id "rails-api"
;;     :item {...}
;;     :excerpts [{:text "...how to build a Rails API..."
;;                 :matched-terms ["rails" "api"]
;;                 :source :content}
;;                {:text "...Rails developers often..."
;;                 :matched-terms ["rails"]
;;                 :source :content}]}
```

### 2. Extract Excerpts from Custom Text

```clojure
(require '[alert-scout.excerpts :as excerpts])

;; Extract excerpts from text
(def sample-text "Learn how to build a Rails API for your application. Rails is great for rapid development.")

(excerpts/generate-excerpts
  sample-text
  ["rails" "api"]
  {:context-chars 50 :max-excerpts 3})

;; => [{:text "Learn how to build a Rails API for your application. Rails..."
;;      :matched-terms ["rails" "api"]
;;      :source :content}  ;; Note: source added by caller
;;     {:text "...API for your application. Rails is great..."
;;      :matched-terms ["rails"]
;;      :source :content}]
```

### 3. Generate Excerpts from Feed Item

```clojure
;; Create a test feed item
(def test-item
  {:feed-id "test"
   :title "Building Rails API"
   :content "Learn how to build a Rails API for modern applications. Rails provides excellent tools for API development."
   :link "https://example.com"
   :published-at (java.util.Date.)})

;; Extract matched terms
(def matched-terms ["rails" "api"])

;; Generate excerpts for the item
(excerpts/generate-excerpts-for-item test-item matched-terms)

;; => [{:text "Building Rails API"
;;      :matched-terms ["rails" "api"]
;;      :source :title}
;;     {:text "Learn how to build a Rails API for modern..."
;;      :matched-terms ["rails" "api"]
;;      :source :content}
;;     {:text "...Rails provides excellent tools for API development."
;;      :matched-terms ["rails" "api"]
;;      :source :content}]
```

## Advanced Usage

### 4. Find Term Positions in Text

```clojure
;; Find all occurrences of a term (case-insensitive)
(excerpts/find-term-positions
  "Rails is great. Many Rails developers love Rails."
  "rails")

;; => [{:start 0 :end 5 :term "rails"}
;;     {:start 22 :end 27 :term "rails"}
;;     {:start 45 :end 50 :term "rails"}]
```

### 5. Extract Single Excerpt with Context

```clojure
;; Extract excerpt around a specific position
(def text "How to build a Rails API for modern web applications")
(def position {:start 15 :end 20 :term "rails"})

(excerpts/extract-excerpt text position 20)

;; => {:text "How to build a Rails API for modern..."
;;     :start 0
;;     :end 40
;;     :position {:start 15 :end 20 :term "rails"}}
```

### 6. Consolidate Overlapping Excerpts

```clojure
;; Create overlapping excerpt candidates
(def excerpts
  [{:text "...build a Rails API..."
    :matched-terms ["rails"]
    :source :content
    :start 10 :end 30}
   {:text "...Rails API for modern..."
    :matched-terms ["api"]
    :source :content
    :start 20 :end 40}])

;; Consolidate (merge threshold: 20 chars)
(excerpts/consolidate-excerpts excerpts 20)

;; => [{:text "...build a Rails API for modern..."
;;      :matched-terms ["rails" "api"]
;;      :source :content
;;      :start 10 :end 40}]
```

## Display and Export

### 7. Display Alerts with Excerpts (Terminal)

```clojure
;; Terminal output uses ANSI colors automatically
(core/run-once)

;; Output:
;; ■ MATCH [hn] Rule: rails-api User: alice
;;   Building Rails API
;;   https://example.com/item/123
;;   Published: 2025-12-30 10:30
;;   [Title] Building **Rails** **API**
;;   [Content] ...how to build a **Rails** **API** for your...
;;   [Content] ...**Rails** developers often use REST...
```

### 8. Export to Markdown

```clojure
;; Get alerts
(def result (core/run-once))

;; Export to markdown file
(core/save-alerts! (:alerts result) "reports/daily-alerts.md" :markdown)

;; File contents:
;; ## Building Rails API
;;
;; - **Feed**: hn
;; - **Rule**: rails-api (User: alice)
;; - **Link**: https://example.com/item/123
;; - **Published**: 2025-12-30 10:30
;;
;; **Matched Content:**
;; - [Title] Building **Rails** **API**
;; - [Content] ...how to build a **Rails** **API** for your...
```

### 9. Export to EDN

```clojure
;; Export to EDN file (structured data)
(core/save-alerts! (:alerts result) "data/alerts-with-excerpts.edn" :edn)

;; File contents:
;; [{:user-id "alice"
;;   :rule-id "rails-api"
;;   :item {...}
;;   :excerpts [{:text "...Rails API..."
;;               :matched-terms ["rails" "api"]
;;               :source :content}]}]
```

## Schema Validation

### 10. Validate Excerpt Data

```clojure
(require '[alert-scout.schemas :as schemas])

;; Check if excerpt is valid
(schemas/valid? schemas/Excerpt
  {:text "...Rails API..."
   :matched-terms ["rails" "api"]
   :source :content})
;; => true

;; Check invalid excerpt
(schemas/valid? schemas/Excerpt
  {:text ""  ;; Empty text not allowed
   :matched-terms ["rails"]
   :source :content})
;; => false

;; Get validation errors
(schemas/explain schemas/Excerpt
  {:text ""
   :matched-terms []
   :source :body})
;; => {:text ["should have at least 1 characters"]
;;     :matched-terms ["should have at least 1 elements"]
;;     :source ["should be either :title or :content"]}
```

### 11. Validate Alert with Excerpts

```clojure
;; Valid alert with excerpts
(schemas/valid? schemas/Alert
  {:user-id "alice"
   :rule-id "test"
   :item test-item
   :excerpts [{:text "..." :matched-terms ["x"] :source :content}]})
;; => true

;; Valid alert without excerpts (backwards compatible)
(schemas/valid? schemas/Alert
  {:user-id "alice"
   :rule-id "test"
   :item test-item})
;; => true
```

## Testing Excerpts

### 12. Generate Test Data

```clojure
(require '[malli.generator :as mg])

;; Generate random valid excerpt
(mg/generate schemas/Excerpt)
;; => {:text "abc def ghi"
;;     :matched-terms ["x" "y"]
;;     :source :content}

;; Generate 10 random excerpts
(mg/sample schemas/Excerpt 10)
```

### 13. Test Excerpt Generation with Edge Cases

```clojure
;; Empty content
(excerpts/generate-excerpts-for-item
  {:title "Test" :content nil}
  ["test"])
;; => [{:text "Test" :matched-terms ["test"] :source :title}]

;; Short content
(excerpts/generate-excerpts-for-item
  {:title "Rails" :content "Short Rails post"}
  ["rails"])
;; => [{:text "Rails" :matched-terms ["rails"] :source :title}
;;     {:text "Short Rails post" :matched-terms ["rails"] :source :content}]

;; Many matches
(def long-text (str/join " " (repeat 100 "Rails is great")))
(count (excerpts/generate-excerpts long-text ["rails"] {:max-excerpts 3}))
;; => 3 (limited per spec)
```

## Performance Testing

### 14. Measure Excerpt Generation Time

```clojure
;; Create realistic test data
(def realistic-content
  (slurp "test/fixtures/sample-article.txt"))  ; 2000 chars

;; Measure time (target: <5ms)
(time
  (dotimes [_ 100]
    (excerpts/generate-excerpts realistic-content ["rails" "api"] {})))
;; => "Elapsed time: 45.3 msecs" (0.45ms per call)

;; Measure full alert generation
(time (core/run-once))
;; => "Elapsed time: 234 msecs" (including feed fetching)
```

## Common Patterns

### 15. Debugging Excerpt Generation

```clojure
;; Enable detailed logging
(def result
  (core/run-once))

;; Inspect alerts with excerpts
(doseq [alert (:alerts result)]
  (println "Alert:" (:rule-id alert))
  (println "Excerpts:" (count (:excerpts alert)))
  (doseq [excerpt (:excerpts alert)]
    (println "  Source:" (:source excerpt))
    (println "  Terms:" (:matched-terms excerpt))
    (println "  Text:" (:text excerpt))))
```

### 16. Customizing Excerpt Context

```clojure
;; Currently fixed at 50 characters (per spec)
;; To change (for experimentation only):

(def custom-opts
  {:context-chars 30      ; Less context
   :max-excerpts 5})      ; More excerpts

(excerpts/generate-excerpts sample-text ["rails"] custom-opts)
```

## Troubleshooting

### No Excerpts Generated

**Problem**: Alert has empty `:excerpts []`

**Causes**:
1. Content is nil or empty
2. Matched terms not found in content (case-sensitive issue?)
3. Excerpt generation failed

**Debug**:
```clojure
;; Check if terms match
(def item {...})
(def terms ["rails" "api"])

;; Manual check
(str/includes?
  (str/lower-case (str (:title item) " " (:content item)))
  (str/lower-case (first terms)))
;; => Should be true

;; Check excerpts directly
(excerpts/generate-excerpts-for-item item terms)
```

### Reflection Warnings

**Problem**: `lein check` shows reflection warnings

**Solution**: Ensure all Java interop has type hints

```clojure
;; Bad
(.indexOf text term)

;; Good
(.indexOf ^String text ^String term)
```

### Schema Validation Errors

**Problem**: Invalid excerpt data

**Solution**: Use `schemas/explain` to see detailed errors

```clojure
(schemas/explain schemas/Excerpt invalid-excerpt)
;; Shows exactly which fields are invalid
```

## Summary

**Core Functions**:
- `(core/run-once)` - Generate alerts with excerpts
- `(excerpts/generate-excerpts text terms opts)` - Extract excerpts from text
- `(excerpts/generate-excerpts-for-item item terms)` - Generate from feed item
- `(core/save-alerts! alerts path format)` - Export to markdown/EDN

**REPL Workflow**:
1. Start REPL: `lein repl`
2. Run and inspect: `(def r (core/run-once))`
3. Examine excerpts: `(-> r :alerts first :excerpts)`
4. Export if needed: `(core/save-alerts! (:alerts r) "out.md" :markdown)`

**Testing**:
- Unit tests: `lein test alert-scout.excerpts-test`
- Integration tests: `lein test alert-scout.matcher-test`
- Manual testing: Use REPL examples above

For complete implementation details, see:
- `src/alert_scout/excerpts.clj` - Core excerpt logic
- `src/alert_scout/matcher.clj` - Integration point
- `src/alert_scout/core.clj` - Display and export

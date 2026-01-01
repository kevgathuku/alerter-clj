# Data Model: Content Excerpts in Alerts

**Feature**: Content Excerpts in Alerts
**Phase**: 1 (Design & Contracts)
**Date**: 2025-12-30

## Overview

This document defines the data structures for excerpt generation, following the project's schema-first approach using Malli for runtime validation.

## Core Entities

### Excerpt

**Purpose**: Represents a snippet of text showing where a term matched with surrounding context.

**Schema**:
```clojure
(def Excerpt
  [:map {:description "A text excerpt showing matched content with context"}
   [:text [:string {:min 1 :description "Excerpt text with ellipsis if truncated"}]]
   [:matched-terms [:vector [:string {:min 1}] {:min 1 :description "Terms that matched in this excerpt"}]]
   [:source [:enum :title :content {:description "Where the excerpt came from"}]]])
```

**Attributes**:
- `text` (string, required): The excerpt text including surrounding context and ellipsis (`...`) if truncated
  - Example: `"...how to build a Rails API for your application..."`
  - Constraints: Non-empty, max ~150 characters (50 before + 50 after + term + ellipsis)
- `matched-terms` (vector of strings, required): List of terms that matched in this excerpt
  - Example: `["rails" "api"]`
  - Constraints: At least one term, all lowercase for consistency
- `source` (keyword, required): Indicates whether excerpt came from title or content
  - Values: `:title` or `:content`
  - Used for display labeling: "[Title]" or "[Content]"

**Relationships**:
- Belongs to one Alert (many excerpts per alert, max 3)
- References matched terms from the Rule that triggered the alert

**Validation Rules**:
- Text must be non-empty (at least one character)
- At least one matched term must be present
- Source must be either :title or :content (enforced by enum)

**Example Instances**:
```clojure
{:text "...how to build a Rails API for your..."
 :matched-terms ["rails" "api"]
 :source :content}

{:text "Building Rails API"
 :matched-terms ["rails" "api"]
 :source :title}
```

### Alert (Enhanced)

**Purpose**: Existing entity enhanced with excerpt support.

**Schema** (modifications only):
```clojure
(def Alert
  [:map {:description "Alert generated when feed item matches rule"}
   [:rule-id [:string {:min 1}]]
   [:item FeedItem]
   ;; NEW FIELD
   [:excerpts {:optional true}
    [:vector Excerpt
     {:max 3 :description "Up to 3 excerpts showing matched content"}]]])
```

**New Attribute**:
- `excerpts` (vector of Excerpt, optional): Excerpts showing where terms matched
  - Constraints: Maximum 3 excerpts per alert (per spec requirement FR-005)
  - Optional: Missing if excerpt generation disabled or no matches found
  - Ordered: Most relevant excerpts first (implementation detail)

**Backwards Compatibility**:
- `:excerpts` field is optional - existing alerts without excerpts remain valid
- Export functions handle both with/without excerpts gracefully

**Note**: The `:user-id` field was removed during implementation as the system operates on rule-based matching without user grouping.

**Example Instance**:
```clojure
{:rule-id "rails-api"
 :item {:feed-id "hn"
        :title "Building Rails API"
        :link "https://example.com"
        :content "Learn how to build a Rails API for your application..."
        :published-at #inst "2025-12-30T10:00:00.000-00:00"}
 :excerpts [{:text "Building Rails API"
             :matched-terms ["rails" "api"]
             :source :title}
            {:text "...how to build a Rails API for your application..."
             :matched-terms ["rails" "api"]
             :source :content}]}
```

### ProcessFeedResult

**Purpose**: Return value from processing a single feed, containing feed info and results.

**Schema**:
```clojure
(def ProcessFeedResult
  [:map {:description "Result of processing a single feed"}
   [:feed Feed]
   [:items [:vector FeedItem]]
   [:alerts [:vector Alert]]
   [:latest-item [:maybe FeedItem]]
   [:item-count :int]])
```

**Attributes**:
- `feed` (Feed map, required): The feed that was processed with :feed-id and :url
- `items` (vector of FeedItem, required): New items found in this feed
- `alerts` (vector of Alert, required): Alerts generated from matching items
- `latest-item` (FeedItem or nil, required): Most recent item by :published-at (for checkpoint)
- `item-count` (int, required): Count of new items processed

**Usage**: Returned by `core/process-feed`, collected in `core/run-once` for aggregation and side effects.

**Example Instance**:
```clojure
{:feed {:feed-id "hn" :url "https://hnrss.org/frontpage"}
 :items [{:feed-id "hn" :item-id "123" ...}]
 :alerts [{:rule-id "rails-api" :item {...} :excerpts [...]}]
 :latest-item {:feed-id "hn" :item-id "123" :published-at #inst "2025-12-30T12:00:00.000-00:00" ...}
 :item-count 1}
```

## Internal Data Structures

### TermPosition (intermediate, not validated)

**Purpose**: Internal representation of where a term was found in text.

**Structure** (plain map, no schema):
```clojure
{:start 16        ;; Character index where term starts
 :end 21          ;; Character index where term ends
 :term "rails"}   ;; The matched term (original case)
```

**Usage**:
- Created by `find-term-positions` function
- Consumed by `extract-excerpt` to build excerpts
- Not exposed in API (internal implementation detail)

### ExcerptCandidate (intermediate, not validated)

**Purpose**: Excerpt with position information before consolidation.

**Structure** (plain map, no schema):
```clojure
{:text "...how to build a Rails API..."
 :matched-terms ["rails" "api"]
 :source :content
 :start 0          ;; Start position in original text
 :end 30}          ;; End position in original text
```

**Usage**:
- Created during excerpt extraction with context
- Contains position info for overlap detection
- Consolidation step merges nearby candidates
- Position fields removed in final Excerpt output

## Schema Validation Points

### 1. Excerpt Creation (alert-scout.excerpts namespace)

```clojure
(defn generate-excerpts
  "Generate validated excerpts from text and matched terms."
  [text matched-terms opts]
  ;; ... extraction logic ...
  (let [excerpts (build-excerpt-candidates text matched-terms opts)
        consolidated (consolidate-excerpts excerpts)
        limited (take 3 consolidated)
        final (mapv #(select-keys % [:text :matched-terms :source]) limited)]
    ;; Validate each excerpt
    (mapv #(schemas/validate schemas/Excerpt %) final)))
```

**Validation Timing**: Before returning from `generate-excerpts`

**Error Handling**: Invalid excerpts throw exception with clear Malli error message

### 2. Alert Enhancement (alert-scout.matcher namespace)

```clojure
(defn match-item
  "Return alerts with validated excerpts."
  [rules-by-user item]
  (for [[user-id rules] rules-by-user
        rule rules
        :when (match-rule? rule item)
        :let [matched-terms (get-matched-terms rule item)
              excerpts (excerpts/generate-excerpts-for-item item matched-terms)
              alert {:user-id user-id
                     :rule-id (:id rule)
                     :item item
                     :excerpts excerpts}]]
    ;; Validate complete alert
    (schemas/validate schemas/Alert alert)))
```

**Validation Timing**: After adding excerpts to alert

**Error Handling**: Invalid alert throws exception (should not happen if Excerpt validation passed)

## State Transitions

Excerpts are **immutable once created**. No state transitions.

Alert lifecycle with excerpts:
1. **Created**: Alert with empty `:excerpts []` or no `:excerpts` field
2. **Enhanced**: Excerpts added via matcher (`:excerpts [{...} {...}]`)
3. **Displayed**: Formatted for terminal output with ANSI colors
4. **Exported**: Serialized to markdown or EDN format

No updates or deletions - excerpts are regenerated each run.

## Data Volume Estimates

### Per Run (typical)
- Feed items processed: 10-100
- Alerts generated: 5-20 (depends on rules)
- Excerpts per alert: 1-3 (average 2)
- Total excerpts: 10-40 per run

### Per Excerpt
- Text length: ~100 characters average (50 before + 50 after + term)
- Matched terms: 1-3 terms per excerpt
- Memory: ~200 bytes per excerpt (text + overhead)

### Total Memory
- 40 excerpts * 200 bytes = ~8KB per run
- Negligible compared to feed item content (~100KB)

## Edge Cases

### Empty Content
**Case**: Feed item has nil or empty content
**Handling**: Only extract from title if available, no excerpts if both nil
```clojure
{:excerpts []}  ;; or omit :excerpts field
```

### Short Content
**Case**: Content is shorter than 50 characters
**Handling**: Show entire content without ellipsis
```clojure
{:text "Short Rails post"
 :matched-terms ["rails"]
 :source :content}
```

### Many Matches
**Case**: Term appears 50+ times in content
**Handling**: Limit to first 3 consolidated excerpts (per FR-005)

### Overlapping Excerpts
**Case**: Multiple terms match close together (<20 chars)
**Handling**: Consolidate into single excerpt with all matched terms
```clojure
;; Before consolidation:
[{:text "...Rails API development..." :matched-terms ["rails"] ...}
 {:text "...Rails API development..." :matched-terms ["api"] ...}]

;; After consolidation:
[{:text "...Rails API development..." :matched-terms ["rails" "api"] :source :content}]
```

## Schema Testing Strategy

### Unit Tests (test/alert_scout/schemas_test.clj)

```clojure
(deftest test-excerpt-schema
  (testing "Valid excerpt"
    (is (schemas/valid? schemas/Excerpt
          {:text "...Rails API..."
           :matched-terms ["rails" "api"]
           :source :content})))

  (testing "Invalid excerpt - empty text"
    (is (not (schemas/valid? schemas/Excerpt
               {:text ""
                :matched-terms ["rails"]
                :source :content}))))

  (testing "Invalid excerpt - wrong source"
    (is (not (schemas/valid? schemas/Excerpt
               {:text "text"
                :matched-terms ["rails"]
                :source :body})))))  ;; Should be :title or :content

(deftest test-alert-with-excerpts
  (testing "Alert with excerpts"
    (is (schemas/valid? schemas/Alert
          {:user-id "alice"
           :rule-id "test"
           :item (valid-feed-item)
           :excerpts [{:text "..." :matched-terms ["x"] :source :content}]})))

  (testing "Alert without excerpts (backwards compatible)"
    (is (schemas/valid? schemas/Alert
          {:user-id "alice"
           :rule-id "test"
           :item (valid-feed-item)}))))
```

### Property-Based Tests

```clojure
(deftest test-excerpt-generation
  (testing "Generated excerpts are valid"
    (let [excerpts (mg/sample schemas/Excerpt 10)]
      (is (every? #(schemas/valid? schemas/Excerpt %) excerpts)))))
```

## Migration Path

**Phase**: No migration needed (new feature)

**Backwards Compatibility**:
- Existing code without excerpt support continues working
- `:excerpts` field is optional on Alert schema
- Display functions handle missing excerpts gracefully

**Data Validation**:
- Validation happens at runtime (Malli)
- Invalid data fails fast with clear error messages
- No persisted data (excerpts regenerated each run)

## Summary

| Entity | Schema | Validation Point | Max Instances |
|--------|--------|------------------|---------------|
| Excerpt | `[:map [:text ...] [:matched-terms ...] [:source ...]]` | `generate-excerpts` return | 3 per alert |
| Alert (enhanced) | `[:map ... [:excerpts {:optional true} ...]]` | `match-item` return | ~20 per run |
| TermPosition | No schema (internal) | Not validated | ~10 per item |
| ExcerptCandidate | No schema (internal) | Not validated | ~5 per item |

**Key Principles**:
- Schema-first design (constitution principle II)
- Immutable data structures (constitution principle I)
- Validate at boundaries (function returns)
- Clear error messages (Malli explanations)

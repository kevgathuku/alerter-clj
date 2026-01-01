# Research: Content Excerpts in Alerts

**Feature**: Content Excerpts in Alerts
**Phase**: 0 (Research)
**Date**: 2025-12-30

## Research Areas

### 1. Text Processing Patterns in Clojure

**Question**: What are the best practices for efficient text search and extraction in Clojure?

**Decision**: Use built-in `clojure.string` functions with careful attention to string indexing

**Rationale**:
- `clojure.string/index-of` for finding term positions (case-insensitive via `lower-case`)
- `subs` for extracting substrings with start/end indices
- No regex needed for simple term matching (faster, simpler)
- String operations are immutable and thread-safe by default

**Alternatives Considered**:
- **Regex patterns**: More flexible but slower, overkill for literal term matching
- **Java String methods directly**: Would require type hints, less idiomatic
- **External text processing library**: Unnecessary dependency, built-ins sufficient

**Implementation Pattern**:
```clojure
(defn find-term-positions
  "Find all case-insensitive positions of term in text."
  [text term]
  (let [text-lower (str/lower-case text)
        term-lower (str/lower-case term)
        term-len (count term-lower)]
    (loop [start 0 positions []]
      (if-let [pos (str/index-of text-lower term-lower start)]
        (recur (+ pos term-len)
               (conj positions {:start pos :end (+ pos term-len) :term term}))
        positions))))
```

### 2. Word Boundary Detection

**Question**: How to truncate excerpts at word boundaries without breaking mid-word?

**Decision**: Use `clojure.string/last-index-of` to find last space before truncation point

**Rationale**:
- Simple and effective for English text
- No regex needed - just search for nearest space
- Handles edge case where no space found (use original boundary)
- Aligns with constitution's simplicity principle

**Alternatives Considered**:
- **Regex word boundaries** (`\b`): More complex, Unicode edge cases
- **Sentence detection**: Over-engineered for this use case
- **Character-based word detection**: Complex, language-specific

**Implementation Pattern**:
```clojure
(defn find-word-boundary
  "Find nearest space before/after position for word-safe truncation."
  [text position direction]
  (case direction
    :before (or (str/last-index-of text " " position) position)
    :after  (or (str/index-of text " " position) position)))
```

### 3. Excerpt Consolidation Algorithm

**Question**: How to merge overlapping excerpts efficiently?

**Decision**: Sort by position, then reduce with overlap detection

**Rationale**:
- Functional approach using `reduce` (constitution principle I)
- Single pass after sorting (O(n log n) overall)
- Merge threshold configurable (20 characters default)
- Immutable data structures throughout

**Alternatives Considered**:
- **Interval tree data structure**: Over-engineered, O(n log n) not needed
- **Greedy merge on-the-fly**: Would require mutation or multiple passes
- **No consolidation**: Would produce redundant excerpts

**Implementation Pattern**:
```clojure
(defn consolidate-excerpts
  "Merge excerpts that overlap or are within merge-threshold chars."
  [excerpts merge-threshold]
  (let [sorted (sort-by :start excerpts)]
    (reduce
      (fn [result excerpt]
        (if-let [last-excerpt (peek result)]
          (if (<= (- (:start excerpt) (:end last-excerpt)) merge-threshold)
            ;; Merge: extend last excerpt's end, combine terms
            (conj (pop result)
                  (assoc last-excerpt
                    :end (:end excerpt)
                    :matched-terms (distinct (concat (:matched-terms last-excerpt)
                                                    (:matched-terms excerpt)))))
            ;; No overlap: add as separate excerpt
            (conj result excerpt))
          [excerpt]))
      []
      sorted)))
```

### 4. Highlighting Strategies

**Question**: How to highlight matched terms in different output formats?

**Decision**: Format-specific highlighting functions with term replacement

**Rationale**:
- Terminal: Existing ANSI color functions in core.clj (reuse)
- Markdown: Simple `**term**` wrapping
- EDN: No highlighting (structured data only)
- Case-insensitive replacement using regex with `(?i)` flag

**Alternatives Considered**:
- **HTML output**: Not in scope (terminal and markdown only)
- **Configurable highlighting styles**: Over-engineered, fixed formats sufficient
- **Custom markup language**: Unnecessary complexity

**Implementation Pattern**:
```clojure
(defn highlight-terms-terminal
  "Highlight terms using ANSI colors."
  [text matched-terms color-fn]
  (reduce
    (fn [result term]
      (let [pattern (re-pattern (str "(?i)" (java.util.regex.Pattern/quote term)))]
        (str/replace result pattern
                     (fn [match] (color-fn :yellow (color-fn :bold match))))))
    text
    matched-terms))

(defn highlight-terms-markdown
  "Highlight terms using markdown bold."
  [text matched-terms]
  (reduce
    (fn [result term]
      (let [pattern (re-pattern (str "(?i)" (java.util.regex.Pattern/quote term)))]
        (str/replace result pattern
                     (fn [match] (str "**" match "**")))))
    text
    matched-terms))
```

### 5. Schema Design for Excerpts

**Question**: What schema structure for excerpt data?

**Decision**: Malli schemas following existing Alert Scout patterns

**Rationale**:
- Consistent with existing schema-first approach (constitution principle II)
- Validates excerpt structure at boundaries
- Supports property-based testing with `malli.generator`
- Clear error messages for invalid data

**Schema Structure**:
```clojure
;; New schema
(def Excerpt
  [:map
   [:text [:string {:min 1}]]
   [:matched-terms [:vector [:string {:min 1}]]]
   [:source [:enum :title :content]]])

;; Enhanced existing schema
(def Alert
  [:map
   [:user-id [:string {:min 1}]]
   [:rule-id [:string {:min 1}]]
   [:item FeedItem]
   [:excerpts {:optional true} [:vector Excerpt]]])  ;; NEW field
```

### 6. Performance Optimization

**Question**: How to ensure <5ms excerpt generation per alert?

**Decision**: Process only first 5000 characters of content, lazy sequences

**Rationale**:
- Most relevant matches in first 5000 chars (assumption from spec)
- Lazy sequences avoid realizing unnecessary data
- String operations are O(n) but n is bounded
- No premature optimization - profile first, optimize if needed

**Performance Characteristics**:
- Term position finding: O(n*m) where n=content length, m=term count
- Excerpt extraction: O(k) where k=number of matches (max 3)
- Consolidation: O(k log k) for sorting (k small)
- Total: Dominated by O(n*m), but n≤5000, m typically 2-5

**Optimization Strategies**:
1. Limit content length processed (5000 chars)
2. Use lazy sequences for position finding
3. Early termination when 3 excerpts found
4. Cache lowercased text (don't lowercase repeatedly)

**Benchmarking Plan**:
- Test with 500-5000 char content
- Test with 1, 5, 10 matching terms
- Measure on typical laptop (target <5ms)
- Use `(time ...)` for REPL-based profiling

### 7. Integration with Existing Matcher

**Question**: Where to hook excerpt generation into the matching pipeline?

**Decision**: Extend `match-item` function in matcher.clj to include excerpts

**Rationale**:
- Matcher already knows which terms matched (can extract from rule)
- Single pass through matched items (no duplicate work)
- Pure function transformation (constitution principle I)
- Returns enhanced alert with `:excerpts` field

**Integration Point**:
```clojure
;; In alert-scout.matcher namespace
(require '[alert-scout.excerpts :as excerpts])

(defn get-matched-terms
  "Extract terms that actually matched from rule."
  [rule item]
  (let [text (str/lower-case (str (:title item) " " (:content item)))
        must-matches (filter #(str/includes? text (str/lower-case %)) (:must rule))
        should-matches (filter #(str/includes? text (str/lower-case %)) (:should rule))]
    (vec (concat must-matches should-matches))))

(defn match-item
  "Return alerts with excerpts for matching items."
  [rules-by-user item]
  (for [[user-id rules] rules-by-user
        rule rules
        :when (match-rule? rule item)
        :let [matched-terms (get-matched-terms rule item)
              item-excerpts (excerpts/generate-excerpts-for-item item matched-terms)]]
    {:user-id user-id
     :rule-id (:id rule)
     :item item
     :excerpts item-excerpts}))
```

## Summary of Key Decisions

| Area | Decision | Impact |
|------|----------|--------|
| Text Processing | Built-in `clojure.string` functions | No new dependencies, idiomatic Clojure |
| Word Boundaries | Find nearest space before/after truncation | Simple, handles 90% of cases |
| Consolidation | Sort + reduce with overlap detection | O(n log n), functional approach |
| Highlighting | Format-specific functions (ANSI, markdown) | Reuses existing patterns |
| Schema | Malli schema for Excerpt, enhance Alert | Consistent with constitution |
| Performance | Limit to 5000 chars, lazy sequences | Meets <5ms target |
| Integration | Extend matcher's `match-item` function | Single pass, pure transformation |

## Next Phase

All research complete. Ready for Phase 1: Design & Contracts
- Create data-model.md with detailed Excerpt schemas
- Generate quickstart.md with REPL usage examples
- No API contracts needed (internal library, not web service)

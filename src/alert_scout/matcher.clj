(ns alert-scout.matcher
  (:require [clojure.string :as str]
            [alert-scout.excerpts :as excerpts]))

(defn text
  "Extract and normalize text from feed item - title and content"
  [item]
  (->> [(:title item) (:content item)]
       (remove nil?)
       (str/join " ")
       str/lower-case))

(defn contains-term? [text term]
  (str/includes? (str/lower-case text) (str/lower-case term)))

(defn apply-rule
  "Apply a rule to an item and return matched terms if the rule passes.

  Checks all rule constraints (must, should, must-not, min-should-match) in a single pass.

  Args:
    rule - Rule map with :must, :should, :must-not, :min-should-match fields
    item - FeedItem to match against

  Returns:
    Vector of matched terms (from must + should) if rule matches
    nil if rule does not match"
  [{:keys [must should must-not min-should-match]
    :or {must [] should [] must-not [] min-should-match 0}}
   item]
  (let [t (text item)
        ;; Filter matched terms in a single pass
        must-matches (filter #(contains-term? t %) must)
        should-matches (filter #(contains-term? t %) should)
        must-not-matches (filter #(contains-term? t %) must-not)]

    ;; Check all constraints
    (when (and
           ;; All must terms present
           (= (count must-matches) (count must))
           ;; No must-not terms present
           (zero? (count must-not-matches))
           ;; Sufficient should terms
           (>= (count should-matches) min-should-match))
      ;; Return matched terms (must + should, distinct)
      (vec (distinct (concat must-matches should-matches))))))

(defn match-item
  "Return vector of alerts for a single item.

  Each alert includes excerpts showing where matched terms appear.

  Args:
    rules - Vector of rule maps
    item - FeedItem to match against rules

  Returns:
    Vector of alert maps with :rule-id, :item, and :excerpts"
  [rules item]
  (for [rule rules
        :let [matched-terms (apply-rule rule item)]
        :when (some? matched-terms)
        :let [item-excerpts (excerpts/generate-excerpts-for-item item matched-terms)]]
    {:rule-id (:id rule)
     :item item
     :excerpts item-excerpts}))

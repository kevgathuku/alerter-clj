(ns repl-scratch
  "REPL scratch pad for experimentation and ad-hoc testing.
  Evaluate forms in comment blocks with vim-fireplace: cpp"
  (:require [alert-scout.core :as core]
            [alert-scout.matcher :as matcher]
            [alert-scout.fetcher :as fetcher]
            [alert-scout.formatter :as formatter]
            [alert-scout.storage :as storage]))

;; ═══════════════════════════════════════════════════════════
;; Testing alerts-summary
;; ═══════════════════════════════════════════════════════════

(comment
  ;; Sample alert data
  (def test-alerts
    [{:rule-id "rule-ai"
      :item {:feed-id "hn"
             :item-id "https://exopriors.com/scry"
             :title "Show HN: Use Claude Code to Query 600 GB Indexes over Hacker News, ArXiv, etc."
             :link "https://exopriors.com/scry"
             :published-at #inst "2025-12-31T07:47:44.000-00:00"
             :content "<a href=\"https://news.ycombinator.com/item?id=46442245\">Comments</a>"
             :categories []}
      :excerpts [{:text "Show HN: Use Claude Code to Query 600 GB Indexes over Hacker News,..."
                  :matched-terms ["claude"]
                  :source :title}]}])

  ;; Test grouping and summary
  (group-by :rule-id test-alerts)
  (println (core/alerts-summary test-alerts))
  (println (core/alerts-summary test-alerts true))  ; with colors
  )

;; ═══════════════════════════════════════════════════════════
;; Testing run-once
;; ═══════════════════════════════════════════════════════════

(comment
  ;; Run feed processing
  (def result (core/run-once))
  
  ;; Inspect results
  (:alerts result)
  (:items-processed result)
  (count (:alerts result))
  
  ;; Look at first alert
  (first (:alerts result))
  
  ;; Check alert excerpts
  (->> result :alerts first :excerpts)
  )

;; ═══════════════════════════════════════════════════════════
;; Testing individual components
;; ═══════════════════════════════════════════════════════════

(comment
  ;; Test feed fetching
  (def feeds (storage/load-feeds "data/feeds.edn"))
  (def first-feed (first feeds))
  (def items (fetcher/fetch-items first-feed))
  
  ;; Test rule matching
  (def rules (storage/load-rules "data/rules.edn"))
  (def first-item (first items))
  (matcher/match-item rules first-item)
  
  ;; Test formatting
  (formatter/format-alert (first test-alerts))
  )

;; ═══════════════════════════════════════════════════════════
;; Quick helpers
;; ═══════════════════════════════════════════════════════════

(comment
  ;; Reload config
  (def rules (storage/load-rules "data/rules.edn"))
  (def feeds (storage/load-feeds "data/feeds.edn"))
  
  ;; Quick run with fresh data
  (storage/load-checkpoints! "data/checkpoints.edn")
  (core/run-once)
  )

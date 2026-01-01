(ns alert-scout.core
  (:require [alert-scout.fetcher :as fetcher]
            [alert-scout.matcher :as matcher]
            [alert-scout.storage :as storage]
            [alert-scout.formatter :as formatter]
            [clojure.string :as str])
  (:import (java.util Date)))

;; --- Load and validate configuration on startup ---
;; All config is validated against schemas. If any validation fails,
;; the namespace will fail to load with clear error messages.
(def rules (storage/load-rules "data/rules.edn"))
(def feeds (storage/load-feeds "data/feeds.edn"))
(storage/load-checkpoints! "data/checkpoints.edn")

;; --- Alert formatting (using formatter namespace) ---
(defn emit-alert
  "Print a formatted alert."
  [alert]
  (println (formatter/format-alert alert)))

(defn alerts-summary
  "Create a summary of all alerts grouped by matched alerts"
  ([alerts] (alerts-summary alerts false))
  ([alerts colorize?]
   (let [colorize (fn [color text] (if colorize? (formatter/colorize color text) text))]
     (if (empty? alerts)
       (str "\n" (colorize :gray "No new alerts found."))
       (let [by-feed (group-by :rule-id alerts)
             total (count alerts)]
         (str "\n" (colorize :bold "═══ SUMMARY ═══")
              "\n" (colorize :green (str "Total alerts: " total))
              "\n"
              (str/join "\n"
                        (for [[feed-id feed-alerts] by-feed]
                          (str "  " (colorize :cyan feed-id) ": "
                               (colorize :yellow (str (count feed-alerts) " alerts")))))
              "\n"))))))

;; --- Fetch, match, emit alerts, update checkpoint ---
(defn process-feed
  "Process a single feed and return its results without side effects."
  [feed]
  (let [{:keys [feed-id]} feed
        last-seen (storage/last-seen feed-id)
        items (->> (fetcher/fetch-items feed)
                   (filter #(when-let [ts (:published-at %)]
                              (or (nil? last-seen) (.after ^Date ts last-seen))))
                   (sort-by :published-at))
        alerts (mapcat #(matcher/match-item rules %) items)]
    {:feed feed
     :items items
     :alerts alerts
     :latest-item (last items)
     :item-count (count items)}))

(defn run-once
  "Process feeds for new items and emit alerts.
   If no feeds provided, uses the feeds loaded from data/feeds.edn.
   Returns a map with :alerts (all alerts found) and :items-processed (total items)."
  ([]
   (run-once feeds))
  ([feeds]
   ;; Process all feeds functionally (no mutation)
   (let [results (map process-feed feeds)
         ;; Aggregate results
         all-alerts (mapcat :alerts results)
         total-items (reduce + 0 (map :item-count results))]

     ;; Perform side effects after data processing
     (doseq [{:keys [alerts latest-item] {:keys [feed-id url]} :feed} results]
       (println (formatter/colorize :gray (str "\n→ Checking feed: " feed-id " (" url ")")))
       (run! emit-alert alerts)
       (when latest-item
         (storage/update-checkpoint! feed-id (:published-at latest-item) "data/checkpoints.edn")))

     (println (alerts-summary all-alerts true))
     (println (formatter/colorize :gray (str "Processed " total-items " new items across " (count feeds) " feeds\n")))

     {:alerts (vec all-alerts)
      :items-processed total-items})))

(defn -main
  "Main entry point for lein run."
  [& args]
  (run-once)
  (shutdown-agents))

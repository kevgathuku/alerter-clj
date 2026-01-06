(ns alert-scout.core
  (:require [alert-scout.fetcher :as fetcher]
            [alert-scout.matcher :as matcher]
            [alert-scout.storage :as storage]
            [alert-scout.formatter :as formatter]
            [clojure.string :as str])
  (:import (java.util Date))
  (:gen-class))

;; --- Load checkpoints on startup ---
(storage/load-checkpoints! "data/checkpoints.edn")

;; --- Alert deduplication ---
(defn deduplicate-alerts-by-url
  "Deduplicate alerts by URL within each rule-id group.
  This removes duplicate articles that match multiple feeds but have the same URL.

  Args:
    alerts - Vector of alert maps

  Returns vector of alerts with duplicates removed (keeps first occurrence per URL per rule-id)."
  [alerts]
  (let [by-rule (group-by :rule-id alerts)]
    (vec (mapcat (fn [[_rule-id rule-alerts]]
                   (vals (reduce (fn [acc alert]
                                   (let [url (get-in alert [:item :link])]
                                     (if (contains? acc url)
                                       acc
                                       (assoc acc url alert))))
                                 {}
                                 rule-alerts)))
                 by-rule))))

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
(defn process-feed!
  "Process a single feed and return its results.
   Side effects: fetches feed from network, prints item titles to stdout.
   If feed fetch fails (HTTP errors, rate limiting, etc), returns empty results
   but processing continues for other feeds.

   Args:
     rules - Vector of rule maps to match against items
     feed  - Feed map with :feed-id and :url"
  [rules feed]
  (let [{:keys [feed-id]} feed
        last-seen (storage/last-seen feed-id)
        ;; fetch-items! returns [] on error, so we can safely continue
        items (->> (fetcher/fetch-items! feed)
                   (filter #(when-let [ts (:published-at %)]
                              (or (nil? last-seen) (.after ^Date ts last-seen))))
                   (sort-by :published-at))
        alerts (mapcat #(matcher/match-item rules %) items)]
    {:feed feed
     :items items
     :alerts alerts
     :latest-item (last items)
     :item-count (count items)}))

(defn process-feeds!
  "Process feeds for new items and generate alerts.

   Fetches items from each feed, filters new items since last checkpoint,
   matches them against rules, generates alerts, and updates checkpoints.

   Side effects:
   - Fetches feeds from network
   - Prints processing status to stdout
   - Emits formatted alerts to stdout
   - Updates checkpoint file with latest item timestamps

   Args:
     checkpoint-path - Path to checkpoint file (e.g., 'data/checkpoints.edn')
     rules           - Vector of rule maps to match against items
     feeds           - Vector of feed maps to process

   Returns:
     Map with:
       :alerts          - Vector of deduplicated alerts
       :items-processed - Total number of new items processed"
  [checkpoint-path rules feeds]
  ;; Process all feeds functionally (no mutation)
  (let [results (map (partial process-feed! rules) feeds)
         ;; Aggregate results
         all-alerts (mapcat :alerts results)
         ;; Deduplicate at the earliest point - same article from multiple feeds
         deduplicated-alerts (deduplicate-alerts-by-url all-alerts)
         total-items (reduce + 0 (map :item-count results))]

     ;; Perform side effects after data processing
     (doseq [{:keys [alerts latest-item items] {:keys [feed-id url]} :feed} results]
       (println (formatter/colorize :gray (str "\n→ Checking feed: " feed-id " (" url ")")))
       ;; Log each item title and link for visibility during processing
       (doseq [{:keys [title link]} items]
         (println (formatter/colorize :gray (str "  • " title " — " link))))
       (run! emit-alert alerts)
       (when latest-item
         (storage/update-checkpoint! feed-id (:published-at latest-item) checkpoint-path)))

     (println (alerts-summary deduplicated-alerts true))
     (println (formatter/colorize :gray (str "Processed " total-items " new items across " (count feeds) " feeds")))
     (when (not= (count all-alerts) (count deduplicated-alerts))
       (println (formatter/colorize :gray (str "Deduplicated " (- (count all-alerts) (count deduplicated-alerts)) " duplicate URLs\n"))))

     {:alerts (vec deduplicated-alerts)
      :items-processed total-items}))

(defn -main
  "Main entry point for lein run.
   Fetches feeds, matches rules, and saves alerts as individual EDN files
   in content/YYYY-MM-DD/{timestamp}.edn"
  [& args]
  (let [rules (storage/load-rules! "data/rules.edn")
        feeds (storage/load-feeds! "data/feeds.edn")
        {:keys [alerts]} (process-feeds! "data/checkpoints.edn" rules feeds)]
    (when (seq alerts)
      (let [now (java.util.Date.)
            date-formatter (java.text.SimpleDateFormat. "yyyy-MM-dd")
            time-formatter (java.text.SimpleDateFormat. "HHmmss")
            date-str (.format date-formatter now)
            timestamp-str (.format time-formatter now)
            path (.getPath ^java.io.File (clojure.java.io/file "content" date-str (str timestamp-str ".edn")))]
        (storage/save-alerts! alerts path :edn)))
    (shutdown-agents)))

(defn -generate-jekyll
  "Generate Jekyll blog post from all alerts saved on a specific date.

   Usage:
     lein generate-jekyll              # Use today's date
     lein generate-jekyll 2026-01-01   # Use specific date

   This reads all EDN alert files from content/{date}/*.edn,
   deduplicates by URL per rule-id (in case alerts were saved from multiple runs),
   and generates a Jekyll markdown post in blog/_posts/{date}-alert-scout-daily-report.markdown"
  [& args]
  (let [date-str (or (first args)
                     (.format (java.text.SimpleDateFormat. "yyyy-MM-dd")
                              (java.util.Date.)))
        ;; Parse date string back to Date for Jekyll formatter
        date-formatter (java.text.SimpleDateFormat. "yyyy-MM-dd")
        date (.parse date-formatter date-str)

        ;; Load all alerts for the specified date
        date-dir-file (clojure.java.io/file "content" date-str)
        all-alerts (vec (if (.exists ^java.io.File date-dir-file)
                          (mapcat (fn [edn-file]
                                    (when (.endsWith ^String (.getName ^java.io.File edn-file) ".edn")
                                      (storage/load-edn! (.getPath ^java.io.File edn-file))))
                                  (.listFiles ^java.io.File date-dir-file))
                          []))
        ;; Deduplicate in case there were multiple runs saving to the same date
        deduplicated-alerts (deduplicate-alerts-by-url all-alerts)]

    (if (seq deduplicated-alerts)
      (do
        (println (formatter/colorize :cyan
                                     (str "Loaded " (count all-alerts)
                                          " alerts for " date-str)))
        (when (not= (count all-alerts) (count deduplicated-alerts))
          (println (formatter/colorize :gray
                                       (str "Deduplicated " (- (count all-alerts) (count deduplicated-alerts))
                                            " duplicate URLs"))))
        (storage/save-alerts-jekyll! deduplicated-alerts "blog" date)
        (println (formatter/colorize :green
                                     (str "✓ Jekyll post generated for " date-str))))
      (println (formatter/colorize :yellow
                                   (str "No alerts found for " date-str))))

    (shutdown-agents)))

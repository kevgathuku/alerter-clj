(ns alert-scout.core
  (:require [alert-scout.fetcher :as fetcher]
            [alert-scout.matcher :as matcher]
            [alert-scout.storage :as storage]
            [clojure.string :as str])
  (:import (java.util Date)))


;; --- Load users / rules / feeds / checkpoints ---
(def users (storage/load-edn "data/users.edn"))
(def rules (storage/load-edn "data/rules.edn"))
(def feeds (storage/load-feeds "data/feeds.edn"))
(storage/load-checkpoints! "data/checkpoints.edn")

(def rules-by-user (matcher/rules-by-user rules))

;; --- ANSI Color codes for terminal output ---
(def colors
  {:reset "\u001b[0m"
   :bold "\u001b[1m"
   :green "\u001b[32m"
   :yellow "\u001b[33m"
   :blue "\u001b[34m"
   :magenta "\u001b[35m"
   :cyan "\u001b[36m"
   :gray "\u001b[90m"})

(defn colorize [color text]
  (str (colors color) text (:reset colors)))

;; --- Alert formatting ---
(defn format-alert
  "Format a single alert for display."
  [{:keys [user-id rule-id item]}]
  (let [{:keys [feed-id title link published-at]} item
        date-str (when published-at (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") published-at))]
    (str "\n"
         (colorize :bold (colorize :green "■ MATCH"))
         " " (colorize :cyan (str "[" feed-id "]"))
         " " (colorize :yellow (str "Rule: " rule-id))
         " " (colorize :gray (str "User: " user-id))
         "\n  " (colorize :bold title)
         "\n  " (colorize :blue link)
         (when date-str (str "\n  " (colorize :gray (str "Published: " date-str)))))))

(defn emit-alert
  "Print a formatted alert."
  [alert]
  (println (format-alert alert)))

(defn format-summary
  "Create a summary of all alerts grouped by user and feed."
  [alerts]
  (if (empty? alerts)
    (str "\n" (colorize :gray "No new alerts found."))
    (let [by-user (group-by :user-id alerts)
          total (count alerts)]
      (str "\n" (colorize :bold (str "═══ SUMMARY ═══"))
           "\n" (colorize :green (str "Total alerts: " total))
           "\n"
           (str/join "\n"
                     (for [[user-id user-alerts] by-user]
                       (let [by-feed (group-by #(get-in % [:item :feed-id]) user-alerts)]
                         (str "  " (colorize :yellow (str user-id ": " (count user-alerts) " alerts"))
                              "\n"
                              (str/join "\n"
                                        (for [[feed-id feed-alerts] by-feed]
                                          (str "    - " (colorize :cyan feed-id) ": " (count feed-alerts))))))))
           "\n"))))

;; --- Fetch, match, emit alerts, update checkpoint ---
(defn process-feed
  "Process a single feed and return its results without side effects."
  [{:keys [feed-id url]}]
  (let [last-seen (storage/last-seen feed-id)
        items (->> (fetcher/fetch-items feed-id url)
                   (filter #(when-let [ts (:published-at %)]
                              (or (nil? last-seen) (.after ^Date ts last-seen))))
                   (sort-by :published-at))
        alerts (mapcat #(matcher/match-item rules-by-user %) items)
        latest-item (last items)]
    {:feed-id feed-id
     :url url
     :items items
     :alerts alerts
     :latest-item latest-item
     :item-count (count items)}))

(defn run-once
  "Process feeds for new items and emit alerts.
   If no feeds provided, uses the feeds loaded from data/feeds.edn.
   Returns a map with :alerts (all alerts found) and :items-processed (total items)."
  ([]
   (run-once feeds))
  ([feeds]
   ;; Process all feeds functionally (no mutation)
   (let [         results (map process-feed feeds)
         ;; Aggregate results
         all-alerts (mapcat :alerts results)
         total-items (reduce + 0 (map :item-count results))]

     ;; Perform side effects after data processing
     (doseq [{:keys [feed-id url alerts latest-item]} results]
       (println (colorize :gray (str "\n→ Checking feed: " feed-id " (" url ")")))
       (doseq [alert alerts]
         (emit-alert alert))
       (when latest-item
         (storage/update-checkpoint! feed-id (:published-at latest-item) "data/checkpoints.edn")))

     (println (format-summary all-alerts))
     (println (colorize :gray (str "Processed " total-items " new items across " (count feeds) " feeds\n")))

     {:alerts (vec all-alerts)
      :items-processed total-items})))


;; --- Export functions ---
(defn alerts->markdown
  "Convert alerts to markdown format."
  [alerts]
  (str "# Alert Scout Report\n\n"
       "Generated: " (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") (java.util.Date.)) "\n\n"
       "Total alerts: " (count alerts) "\n\n"
       (str/join "\n\n"
                 (for [alert alerts]
                   (let [{:keys [user-id rule-id item]} alert
                         {:keys [feed-id title link published-at]} item
                         date-str (when published-at (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") published-at))]
                     (str "## " title "\n\n"
                          "- **Feed**: " feed-id "\n"
                          "- **Rule**: " rule-id " (User: " user-id ")\n"
                          "- **Link**: " link "\n"
                          (when date-str (str "- **Published**: " date-str "\n"))))))))

(defn alerts->json
  "Convert alerts to JSON-like EDN format."
  [alerts]
  (pr-str (mapv (fn [alert]
                  {:user-id (:user-id alert)
                   :rule-id (:rule-id alert)
                   :feed-id (get-in alert [:item :feed-id])
                   :title (get-in alert [:item :title])
                   :link (get-in alert [:item :link])
                   :published-at (str (get-in alert [:item :published-at]))})
                alerts)))

(defn save-alerts!
  "Save alerts to a file in the specified format (:markdown or :edn)."
  [alerts path format]
  (let [content (case format
                  :markdown (alerts->markdown alerts)
                  :edn (alerts->json alerts)
                  (throw (ex-info "Unknown format" {:format format})))]
    (spit path content)
    (println (colorize :green (str "✓ Saved " (count alerts) " alerts to " path)))))

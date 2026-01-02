(ns alert-scout.storage
  (:require [clojure.edn :as edn]
            [clojure.java.io :as io]
            [alert-scout.schemas :as schemas]
            [alert-scout.formatter :as formatter]))

(defn load-edn [path]
  (with-open [r (java.io.PushbackReader. (io/reader path))]
    (edn/read {:eof nil} r)))

(defn save-edn! [path data]
  (spit path (pr-str data)))

(def checkpoints (atom {}))

(defn load-checkpoints! [path]
  (reset! checkpoints (or (load-edn path) {})))

(defn save-checkpoints! [path]
  (save-edn! path @checkpoints))

(defn last-seen [feed-id]
  (get @checkpoints feed-id))

(defn update-checkpoint! [feed-id ts path]
  (swap! checkpoints assoc feed-id ts)
  (save-checkpoints! path))

;; --- Configuration loading with validation ---

(defn load-rules
  "Load rules from an EDN file. Returns a vector of rule maps.
   Validates rules against schema and throws on invalid data."
  [path]
  (let [rules (or (load-edn path) [])]
    (try
      (schemas/validate-rules rules)
      (catch Exception e
        (throw (ex-info (str "Invalid rules in " path)
                        {:path path
                         :errors (:errors (ex-data e))}
                        e))))))

;; --- Feed management ---

(defn load-feeds
  "Load feeds from an EDN file. Returns a vector of feed maps with :feed-id and :url.
   Validates feeds against schema and throws on invalid data."
  [path]
  (let [feeds (or (load-edn path) [])]
    (try
      (schemas/validate-feeds feeds)
      (catch Exception e
        (throw (ex-info (str "Invalid feeds in " path)
                        {:path path
                         :errors (:errors (ex-data e))}
                        e))))))

(defn save-feeds!
  "Save feeds to an EDN file. Validates before saving."
  [path feeds]
  (schemas/validate-feeds feeds)
  (save-edn! path feeds))

(defn add-feed!
  "Add a new feed to the feeds file. Returns the updated feeds vector.
   Validates the new feed before adding."
  [path feed-id url]
  (let [feeds (load-feeds path)
        new-feed (schemas/validate schemas/Feed {:feed-id feed-id :url url})
        updated-feeds (conj feeds new-feed)]
    (save-feeds! path updated-feeds)
    updated-feeds))

(defn remove-feed!
  "Remove a feed by feed-id from the feeds file. Returns the updated feeds vector."
  [path feed-id]
  (let [feeds (load-feeds path)
        updated-feeds (vec (remove #(= (:feed-id %) feed-id) feeds))]
    (save-feeds! path updated-feeds)
    updated-feeds))

(defn get-feed
  "Get a specific feed by feed-id."
  [path feed-id]
  (let [feeds (load-feeds path)]
    (first (filter #(= (:feed-id %) feed-id) feeds))))

;; --- Alert export ---

(defn save-alerts!
  "Save alerts to a file in the specified format (:markdown or :edn)."
  [alerts path format]
  (io/make-parents path)
  (let [content (case format
                  :markdown (formatter/alerts->markdown alerts)
                  :edn (pr-str alerts)
                  (throw (ex-info "Unknown format" {:format format})))]
    (spit path content)
    (println (formatter/colorize :green (str "✓ Saved " (count alerts) " alerts to " path)))))

(defn save-alerts-jekyll!
  "Save alerts as a Jekyll blog post for a single day.

  Args:
    alerts - Vector of alert maps (should already be deduplicated from run-once)
    blog-dir - Path to blog directory (default: 'blog')
    date - java.util.Date for the post (default: today)

  The function will:
  1. Generate Jekyll post with front matter
  2. Save to blog/_posts/YYYY-MM-DD-alert-scout-daily-report.markdown

  Note: Alerts are assumed to be deduplicated by run-once. If calling this
  with alerts from other sources, deduplicate first using core/deduplicate-alerts-by-url."
  ([alerts]
   (save-alerts-jekyll! alerts "blog" (java.util.Date.)))
  ([alerts blog-dir]
   (save-alerts-jekyll! alerts blog-dir (java.util.Date.)))
  ([alerts blog-dir ^java.util.Date date]
   (let [{:keys [filename content]} (formatter/alerts->jekyll alerts date)
         posts-dir (str blog-dir "/_posts")
         path (str posts-dir "/" filename)]
     (io/make-parents path)
     (spit path content)
     (println (formatter/colorize :green
                                  (str "✓ Saved " (count alerts)
                                       " alerts to " path))))))

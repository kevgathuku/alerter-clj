(ns alert-scout.fetcher
  (:require [remus :as remus]))

(defn- extract-http-status
  "Extract HTTP status code from Remus exception message."
  [^Exception e]
  (when-let [msg (.getMessage e)]
    (when-let [[_ code] (re-find #"status: (\d+)" msg)]
      (Integer/parseInt code))))

(defn fetch-feed!
  "Fetch RSS/Atom feed using Remus and return the parsed result.
   Returns nil on error with logging to stderr."
  [url]
  (try
    (remus/parse-url url)
    (catch Exception e
      (let [status (extract-http-status e)
            error-msg (if status
                        (condp = status
                          429 (str "Rate limited (HTTP 429): " url " - Try again later")
                          403 (str "Forbidden (HTTP 403): " url " - Access denied")
                          404 (str "Not found (HTTP 404): " url)
                          500 (str "Server error (HTTP 500): " url " - Try again later")
                          503 (str "Service unavailable (HTTP 503): " url " - Try again later")
                          (str "HTTP error " status ": " url))
                        (str "Failed to fetch " url ": " (.getMessage e)))]
        (binding [*out* *err*]
          (println (str "[ERROR] " error-msg)))
        nil))))

(defn entry->item
  "Normalize feed entry to a Clojure map.
   Takes a Remus entry map and feed-id."
  [entry feed-id]
  (let [;; Extract content value from contents or description
        ;; :contents is a sequence, so use first instead of get-in with index
        content-value (or (some-> entry :contents first :value)
                          (some-> entry :description :value))
        ;; Extract URI or link for item-id
        item-id (or (:uri entry) (:link entry))]
    {:feed-id feed-id
     :item-id item-id
     :title (:title entry)
     :link (:link entry)
     :published-at (or (:published-date entry)
                       (:updated-date entry))
     :content content-value
     :categories (or (:categories entry) [])}))

(defn fetch-items
  "Fetch all new items for a feed, normalized.
   Takes a Feed map with :feed-id and :url keys.
   Returns empty list if feed fetch fails (HTTP errors, rate limiting, etc)."
  [{:keys [feed-id url]}]
  (if-let [result (fetch-feed! url)]
    (let [entries (get-in result [:feed :entries])]
      (map #(entry->item % feed-id) entries))
    []))

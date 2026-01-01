(ns alert-scout.fetcher
  (:import
   (java.net URL)
   (java.io IOException)
   (com.rometools.rome.io SyndFeedInput XmlReader)
   (com.rometools.rome.feed.synd SyndFeed SyndEntry SyndContent SyndCategory)))

(defn- get-http-response-code
  "Extract HTTP response code from exception if available."
  [^Exception e]
  (when-let [msg (.getMessage e)]
    (when-let [[_ code] (re-find #"HTTP response code: (\d+)" msg)]
      (Integer/parseInt code))))

(defn fetch-feed
  "Fetch RSS/Atom feed and return SyndFeed.
   Returns nil on error with logging to stderr."
  [url]
  (try
    (with-open [reader (XmlReader. (URL. url))]
      (.build (SyndFeedInput.) reader))
    (catch IOException e
      (let [http-code (get-http-response-code e)
            error-msg (condp = http-code
                        429 (str "Rate limited (HTTP 429): " url " - Try again later")
                        403 (str "Forbidden (HTTP 403): " url " - Access denied")
                        404 (str "Not found (HTTP 404): " url)
                        500 (str "Server error (HTTP 500): " url " - Try again later")
                        503 (str "Service unavailable (HTTP 503): " url " - Try again later")
                        (str "Failed to fetch " url ": " (.getMessage e)))]
        (binding [*out* *err*]
          (println (str "[ERROR] " error-msg)))
        nil))
    (catch Exception e
      (binding [*out* *err*]
        (println (str "[ERROR] Unexpected error fetching " url ": " (.getMessage e))))
      nil)))

(defn entry->item
  "Normalize feed entry to a Clojure map."
  [^SyndEntry entry feed-id]
  (let [get-content-value (fn [^SyndContent c] (when c (.getValue c)))
        contents (some-> entry .getContents first)
        description (.getDescription entry)]
    {:feed-id feed-id
     :item-id (or (.getUri entry) (.getLink entry))
     :title (.getTitle entry)
     :link (.getLink entry)
     :published-at (or (.getPublishedDate entry)
                       (.getUpdatedDate entry))
     :content (or (get-content-value contents)
                  (get-content-value description))
     :categories (mapv #(.getName ^SyndCategory %) (.getCategories entry))}))

(defn fetch-items
  "Fetch all new items for a feed, normalized.
   Takes a Feed map with :feed-id and :url keys.
   Returns empty list if feed fetch fails (HTTP errors, rate limiting, etc)."
  [{:keys [feed-id url]}]
  (if-let [feed (fetch-feed url)]
    (map #(entry->item % feed-id)
         (.getEntries ^SyndFeed feed))
    []))

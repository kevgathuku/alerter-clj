(ns alert-scout.schemas
  (:require [malli.core :as m]
            [malli.error :as me]
            [malli.util :as mu])
  (:import (java.util Date)))

;; --- Domain Schemas ---

(def Feed
  "Schema for a feed subscription."
  [:map
   [:feed-id [:string {:min 1}]]
   [:url [:string {:min 1}]]])

(def Rule
  "Schema for an alert rule."
  [:map
   [:id [:string {:min 1}]]
   [:must {:optional true} [:vector :string]]
   [:should {:optional true} [:vector :string]]
   [:must-not {:optional true} [:vector :string]]
   [:min-should-match {:optional true} :int]])

(def FeedItem
  "Schema for a normalized feed item."
  [:map
   [:feed-id [:string {:min 1}]]
   [:item-id [:string {:min 1}]]
   [:title [:string {:min 1}]]
   [:link [:string {:min 1}]]
   [:published-at [:maybe inst?]]])

(def Excerpt
  "Schema for a text excerpt showing matched content with context."
  [:map
   [:text [:string {:min 1}]]
   [:matched-terms [:vector {:min 1} [:string {:min 1}]]]
   [:source [:enum :title :content]]])

(def Alert
  "Schema for an alert."
  [:map
   [:rule-id [:string {:min 1}]]
   [:item FeedItem]
   [:excerpts {:optional true} [:vector {:max 3} Excerpt]]])

(def ProcessFeedResult
  "Schema for the result of processing a single feed."
  [:map
   [:feed-id [:string {:min 1}]]
   [:url [:string {:min 1}]]
   [:items [:vector FeedItem]]
   [:alerts [:vector Alert]]
   [:latest-item [:maybe FeedItem]]
   [:item-count :int]])

;; --- Validation Functions ---

(defn validate
  "Validate data against a schema. Returns data if valid, throws on invalid."
  [schema data]
  (if (m/validate schema data)
    data
    (throw (ex-info "Validation failed"
                    {:errors (me/humanize (m/explain schema data))
                     :data data}))))

(defn valid?
  "Check if data is valid according to schema."
  [schema data]
  (m/validate schema data))

(defn explain
  "Return human-readable explanation of validation errors."
  [schema data]
  (me/humanize (m/explain schema data)))

;; --- Helper Functions ---

(defn validate-feeds
  "Validate a collection of feeds."
  [feeds]
  (validate [:vector Feed] feeds))

(defn validate-rules
  "Validate a collection of rules."
  [rules]
  (validate [:vector Rule] rules))

(defn validate-feed-item
  "Validate a single feed item."
  [item]
  (validate FeedItem item))

(defn validate-alert
  "Validate a single alert."
  [alert]
  (validate Alert alert))

;; --- Examples for REPL ---

(comment
  ;; Valid feed
  (valid? Feed {:feed-id "hn" :url "https://news.ycombinator.com/rss"})
  ;; => true

  ;; Invalid feed (missing url)
  (explain Feed {:feed-id "hn"})
  ;; => {:url ["missing required key"]}

  ;; Invalid feed (empty feed-id)
  (explain Feed {:feed-id "" :url "https://example.com"})
  ;; => {:feed-id ["should have at least 1 characters"]}

  ;; Validate collection of feeds
  (validate-feeds [{:feed-id "hn" :url "https://news.ycombinator.com/rss"}
                   {:feed-id "blog" :url "https://kevgathuku.dev/rss.xml"}])

  ;; Generate sample data (for testing)
  (require '[malli.generator :as mg])
  (mg/generate Feed)
  ;; => {:feed-id "...", :url "..."}
  )

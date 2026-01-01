(ns alert-scout.formatter
  "Output formatting functions for alerts (terminal, markdown, EDN)."
  (:require [clojure.string :as str]))

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

(defn colorize
  "Apply ANSI color to text."
  [color text]
  (str (colors color) text (:reset colors)))

;; --- Highlighting Functions ---

(defn highlight-terms-terminal
  "Highlight matched terms using ANSI colors (bold yellow).

  Args:
    text - Text to highlight terms in
    matched-terms - Vector of terms to highlight (case-insensitive)

  Returns text with ANSI color codes around matched terms."
  [text matched-terms]
  (if (or (nil? text) (empty? matched-terms))
    text
    (reduce
      (fn [result term]
        (let [pattern (re-pattern (str "(?i)" (java.util.regex.Pattern/quote term)))]
          (str/replace result pattern
                       (fn [match]
                         (colorize :bold (colorize :yellow match))))))
      text
      matched-terms)))

(defn highlight-terms-markdown
  "Highlight matched terms using markdown bold formatting.

  Args:
    text - Text to highlight terms in
    matched-terms - Vector of terms to highlight (case-insensitive)

  Returns text with **bold** markdown around matched terms."
  [text matched-terms]
  (if (or (nil? text) (empty? matched-terms))
    text
    (reduce
      (fn [result term]
        (let [pattern (re-pattern (str "(?i)" (java.util.regex.Pattern/quote term)))]
          (str/replace result pattern
                       (fn [match]
                         (str "**" match "**")))))
      text
      matched-terms)))

;; --- Alert Formatting ---

(defn- format-excerpt
  "Format a single excerpt for terminal display.

  Args:
    excerpt - Excerpt map with :text, :matched-terms, :source
    highlight-fn - Function to highlight matched terms

  Returns formatted string with source label and highlighted text."
  [excerpt highlight-fn]
  (let [{:keys [text matched-terms source]} excerpt
        source-label (case source
                      :title "[Title]"
                      :content "[Content]"
                      "[Unknown]")
        highlighted-text (highlight-fn text matched-terms)]
    (str "  " (colorize :gray source-label) " " highlighted-text)))

(defn format-alert
  "Format a single alert for terminal display with excerpts.

  Args:
    alert - Alert map with :user-id, :rule-id, :item, :excerpts (optional)

  Returns formatted string with ANSI colors and highlighted excerpts."
  [{:keys [user-id rule-id item excerpts]}]
  (let [{:keys [feed-id title link published-at]} item
        date-str (when published-at
                  (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") published-at))

        ;; Main alert header
        header (str "\n"
                   (colorize :bold (colorize :green "■ MATCH"))
                   " " (colorize :cyan (str "[" feed-id "]"))
                   " " (colorize :yellow (str "Rule: " rule-id))
                   " " (colorize :gray (str "User: " user-id))
                   "\n  " (colorize :bold title)
                   "\n  " (colorize :blue link)
                   (when date-str (str "\n  " (colorize :gray (str "Published: " date-str)))))

        ;; Excerpts section (if present)
        excerpts-section (when (and excerpts (seq excerpts))
                          (str "\n"
                               (str/join "\n"
                                        (map #(format-excerpt % highlight-terms-terminal) excerpts))))]

    (str header excerpts-section)))

;; --- Export Functions ---

(defn alerts->markdown
  "Convert alerts to markdown format with excerpts.

  Args:
    alerts - Vector of alert maps

  Returns markdown string with all alerts formatted."
  [alerts]
  (str "# Alert Scout Report\n\n"
       "Generated: " (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm:ss") (java.util.Date.)) "\n\n"
       "Total alerts: " (count alerts) "\n\n"
       (str/join "\n\n"
                 (for [alert alerts]
                   (let [{:keys [rule-id item excerpts]} alert
                         {:keys [feed-id title link published-at]} item
                         date-str (when published-at
                                   (.format (java.text.SimpleDateFormat. "yyyy-MM-dd HH:mm") published-at))]
                     (str "## " title "\n\n"
                          "- **Feed**: " feed-id "\n"
                          "- **Rule**: " rule-id "\n"
                          "- **Link**: " link "\n"
                          (when date-str (str "- **Published**: " date-str "\n"))
                          (when (and excerpts (seq excerpts))
                            (str "\n**Matched Content:**\n"
                                 (str/join "\n"
                                          (for [excerpt excerpts]
                                            (let [{:keys [text matched-terms source]} excerpt
                                                  source-label (case source
                                                                :title "Title"
                                                                :content "Content"
                                                                "Unknown")
                                                  highlighted-text (highlight-terms-markdown text matched-terms)]
                                              (str "- [" source-label "] " highlighted-text))))
                                 "\n"))))))))

(defn alerts->edn
  "Convert alerts to EDN format with excerpts.

  Args:
    alerts - Vector of alert maps

  Returns EDN string representation of alerts."
  [alerts]
  (pr-str (mapv (fn [alert]
                  {:rule-id (:rule-id alert)
                   :feed-id (get-in alert [:item :feed-id])
                   :title (get-in alert [:item :title])
                   :link (get-in alert [:item :link])
                   :published-at (str (get-in alert [:item :published-at]))
                   :excerpts (:excerpts alert)})
                alerts)))

(ns alert-scout.excerpts
  "Core excerpt extraction logic for showing matched content with context."
  (:require [clojure.string :as str]
            [alert-scout.schemas :as schemas]))

;; --- Helper Functions ---

(defn find-term-positions
  "Find all case-insensitive positions of term in text.

  Returns vector of position maps with :start, :end, and :term keys.
  Example: [{:start 0 :end 5 :term \"rails\"}]"
  [text term]
  (when (and text term (pos? (count text)) (pos? (count term)))
    (let [text-lower (str/lower-case text)
          term-lower (str/lower-case term)
          term-len (count term-lower)]
      (loop [start (long 0) positions []]
        (if-let [pos (str/index-of text-lower term-lower start)]
          (recur (long (+ pos term-len))
                 (conj positions {:start pos :end (+ pos term-len) :term term}))
          positions)))))

(defn- find-word-boundary
  "Find nearest space before or after position for word-safe truncation.

  Direction can be :before or :after.
  Returns original position if no space found."
  [text position direction]
  (when text
    (case direction
      :before (or (str/last-index-of text " " position) position)
      :after  (or (str/index-of text " " position) position)
      position)))

(defn extract-excerpt
  "Extract excerpt around a position with word boundary detection.

  Args:
    text - Source text to extract from
    position - Map with :start, :end, :term keys
    context-chars - Number of characters of context on each side (default 50)

  Returns map with :text, :start, :end, :position keys."
  ([text position]
   (extract-excerpt text position 50))
  ([text position context-chars]
   (when (and text position)
     (let [text-len (count text)
           term-start (:start position)
           term-end (:end position)

           ;; Calculate desired excerpt bounds
           desired-start (max 0 (- term-start context-chars))
           desired-end (min text-len (+ term-end context-chars))

           ;; Adjust to word boundaries
           excerpt-start (if (zero? desired-start)
                           0
                           (find-word-boundary text desired-start :after))
           excerpt-end (if (= desired-end text-len)
                         text-len
                         (find-word-boundary text desired-end :before))

           ;; Safety check: if word boundaries crossed, fall back to desired positions
           [final-start final-end] (if (>= excerpt-start excerpt-end)
                                      [desired-start desired-end]
                                      [excerpt-start excerpt-end])

           ;; Extract text with ellipsis
           needs-start-ellipsis (pos? final-start)
           needs-end-ellipsis (< final-end text-len)

           excerpt-text (subs text final-start final-end)
           final-text (str (when needs-start-ellipsis "...")
                           excerpt-text
                           (when needs-end-ellipsis "..."))]

       {:text final-text
        :start final-start
        :end final-end
        :position position}))))

(defn consolidate-excerpts
  "Merge excerpts that overlap or are within merge-threshold chars.

  Args:
    excerpts - Vector of excerpt maps with :start, :end, :matched-terms, :text (without ellipsis)
    merge-threshold - Distance in chars to merge (default 20)

  Returns vector of consolidated excerpts."
  ([excerpts]
   (consolidate-excerpts excerpts 20))
  ([excerpts merge-threshold]
   (if (empty? excerpts)
     []
     (let [sorted (sort-by :start excerpts)]
       (reduce
        (fn [result excerpt]
          (if-let [last-excerpt (peek result)]
            (if (<= (- (:start excerpt) (:end last-excerpt)) merge-threshold)
               ;; Merge: extend positions and combine terms
              (let [merged {:text (:text excerpt) ;; Will be replaced with merged text
                            :matched-terms (vec (distinct (concat (:matched-terms last-excerpt)
                                                                  (:matched-terms excerpt))))
                            :source (:source last-excerpt)
                            :start (:start last-excerpt)
                            :end (:end excerpt)}]
                (conj (pop result) merged))
               ;; No overlap: add as separate excerpt
              (conj result excerpt))
            [excerpt]))
        []
        sorted)))))

(def content-defaults {:context-chars 50  :max-excerpts 3})

(defn generate-excerpts
  "Generate excerpts from text showing matched terms with context.

  Args:
    text - String to extract excerpts from
    matched-terms - Vector of terms to find and highlight
    opts - Optional map with :context-chars (default 50) and :max-excerpts (default 3)

  Returns vector of Excerpt maps with :text, :matched-terms, :source validated against schema."
  ([text matched-terms]
   (generate-excerpts text matched-terms {}))
  ([text matched-terms opts]
   (if (or (nil? text) (empty? text) (nil? matched-terms) (empty? matched-terms))
     []
     (let [context-chars ((merge content-defaults opts) :context-chars)
           max-excerpts ((merge content-defaults opts) :max-excerpts)

           ;; Limit text length for performance (<5ms requirement)
           text-to-process (if (> (count text) 5000)
                             (subs text 0 5000)
                             text)
           text-len (count text-to-process)

           ;; Find all positions for all terms
           all-positions (mapcat #(find-term-positions text-to-process %) matched-terms)

           ;; Extract excerpts for each position
           excerpt-candidates (map #(let [excerpt (extract-excerpt text-to-process % context-chars)
                                          term (:term (:position excerpt))]
                                      (assoc excerpt :matched-terms [term]))
                                   all-positions)

           ;; Consolidate overlapping excerpts
           consolidated (consolidate-excerpts excerpt-candidates)

           ;; Re-extract text with proper ellipsis for merged excerpts
           with-final-text (mapv (fn [excerpt]
                                   (let [start (:start excerpt)
                                         end (:end excerpt)
                                         needs-start-ellipsis (pos? start)
                                         needs-end-ellipsis (< end text-len)
                                         excerpt-text (subs text-to-process start end)
                                         final-text (str (when needs-start-ellipsis "...")
                                                         excerpt-text
                                                         (when needs-end-ellipsis "..."))]
                                     (assoc excerpt :text final-text)))
                                 consolidated)

           ;; Limit to max excerpts
           limited (take max-excerpts with-final-text)

           ;; Convert to final Excerpt schema format (remove :start, :end, :position)
           final (mapv #(select-keys % [:text :matched-terms :source]) limited)]

       ;; Note: :source will be added by generate-excerpts-for-item
       final))))

(defn generate-excerpts-for-item
  "Generate excerpts from both title and content of a feed item.

  Args:
    item - FeedItem map with :title and :content
    matched-terms - Vector of terms to find

  Returns vector of validated Excerpt maps."
  [item matched-terms]
  (when (and item matched-terms)
    (let [title (:title item)
          content (:content item)

          ;; Generate excerpts from title
          title-excerpts (when (and title (seq title))
                           (map #(assoc % :source :title)
                                (generate-excerpts title matched-terms (assoc content-defaults :max-excerpts 1))))

          ;; Generate excerpts from content
          content-excerpts (when (and content (seq content))
                             (map #(assoc % :source :content)
                                  (generate-excerpts content matched-terms content-defaults)))

          ;; Combine and limit to 3 total
          all-excerpts (vec (take 3 (concat title-excerpts content-excerpts)))]

      ;; Validate each excerpt against schema
      (mapv #(schemas/validate schemas/Excerpt %) all-excerpts))))

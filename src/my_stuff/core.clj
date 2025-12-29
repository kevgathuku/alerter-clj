(ns my-stuff.core
  (:require [clojure.string :as str])
  (:gen-class))

(defn is-same-word [word candidate]
  (= (str/lower-case word) (str/lower-case candidate)))

(let [result (is-same-word "kevin" "KEVin")] (println result))

(comment
  (let [x 3] (inc x))
  (map inc [1 2 3]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

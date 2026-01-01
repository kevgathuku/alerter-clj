(ns my-stuff.core
  (:require [clojure.string :as str])
  (:gen-class))

(defn is-same-word [word candidate]
  (= (str/lower-case word) (str/lower-case candidate)))

(let [result (is-same-word "kevin" "KEVin")] (println result))

(comment
  (let [x 3] (inc x))
  (map inc [1 2
            3])
  (+ 1 2 3)
  (map inc xs)
  (->> xs (filter even?) (map inc))

  (defn load-edn [path]
    (with-open [r (java.io.PushbackReader. (io/reader path))]
      (edn/read r)))

  (println (+ 1 2 3))
  (map (comp (partial * 4) inc) [1 2 3])

  (->> xs (filter even?) (map inc))
  (->> [1 2 3] (filter even?) (map inc))

  (filter even? xs)

  (defn load-edn [path]
    (with-open [r (io/reader path)]
      (edn/read r)))

  (+ 1 2 3))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (println "Hello, World!"))

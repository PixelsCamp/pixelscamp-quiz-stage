(ns pixelsquiz.create-questions
  (:require [pixelsquiz.types :refer :all])
  (:import pixelsquiz.types.Question))

(require '[clojure.string :as str])

(defn test-questions
  ([] (test-questions (Question. 1 :multi 1 "This is the first question" ["Answer 1" "Answer 2" "Answer 3" "Answer 4"])))
  ([prev]
   (let [next-id (inc (:id prev))]
    (lazy-seq
      (cons prev (test-questions (Question. next-id
                                            :multi 1
                                            (str "This is the " next-id "nth question. "
                                                  (apply str (take (rand 25)
                                                                          (repeatedly #(char (+ 32 (rand 30)))))))
                            ["Answer 1" "Answer 2" "Answer 3" "Answer 4"])
                                 )
            )))))

(defn write-test-questions!
  [n]
  (spit "questions.edn" (pr-str (apply vector (cons
                                                (Question. 0 :multi 0 "The test question"
                                                           ["a1" "a2" "a3" "a4"])
                                                (take n (test-questions)))))))

(defn map-questions-txt
  [input]
  (mapv #(Question. %2 :multi 1 (first %1) (vec (rest %1)))
        (map #(str/split % #"\n")
             (str/split (slurp input) #"\n\s*\n"))
        (range 100)
        ))

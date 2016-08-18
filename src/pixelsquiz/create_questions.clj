(ns pixelsquiz.create-questions
  (:require [pixelsquiz.types :refer :all])
  (:import pixelsquiz.types.Question))

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
  (spit "questions.edn" (pr-str (apply vector (cons nil ; humans start at 1
                                      (take n (test-questions)))))))

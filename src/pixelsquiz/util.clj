(ns pixelsquiz.util
  (:gen-class))


(defn scores-to-map
  [scores]
  (mapv (fn [s t] {:score s :team t}) scores [1 2 3 4])
  )

(defn sort-teams-by-scores
  [scores]
  (sort-by :score > (scores-to-map scores))
  )

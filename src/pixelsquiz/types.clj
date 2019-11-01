(ns pixelsquiz.types
  (:gen-class))


(defrecord Event [kind bag-of-props])
(defrecord Question [id kind score text options trivia])
(defrecord Answer [question team-buzzed good-buzz answers scores])
(defrecord Round [number questions scores])
(defrecord GameState [round-index current-round question-index current-question])

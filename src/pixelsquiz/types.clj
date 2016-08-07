(ns pixelsquiz.types
  (:gen-class))


(defrecord Event [kind bag-of-props])
(defrecord Team [playerA playerB])
(defrecord Question [id kind score text options])
(defrecord Answer [question team-buzzed good-buzz answers scores])
(defrecord Round [number teams questions])
(defrecord GameState [round-index current-round question-index current-question])


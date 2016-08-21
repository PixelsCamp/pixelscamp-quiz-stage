(ns pixelsquiz.sounds  
  (:gen-class)
  (:require [dynne.sampled-sound :as s])
  )


(def audio-player (atom nil))

(def sounds {
  :thinking-music (s/read-sound "sounds/blues-prog2.mp3")
  :buzz (s/read-sound "sounds/buzz.mp3")
  :correct (s/read-sound "sounds/correct.mp3")
  :error (s/read-sound "sounds/error.wav")
  :t1 (s/read-sound "sounds/p1.mp3")
  :t2 (s/read-sound "sounds/p2.mp3")
  :t3 (s/read-sound "sounds/p3.mp3")
  :t4 (s/read-sound "sounds/p4.mp3")
  :ping (s/read-sound "sounds/ping.mp3")
})

(defn play-thinking-music
  []
  (reset! audio-player (s/play (:thinking-music sounds))))

(defn stop-thinking-music
  []
  (s/stop @audio-player)
  (reset! audio-player nil))

(defn play 
  [sound]
  (if-let [s (sound sounds)]
    (s/play s)))


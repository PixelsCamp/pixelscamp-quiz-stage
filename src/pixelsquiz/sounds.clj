(ns pixelsquiz.sounds
  (:gen-class)
  (:require [dynne.sampled-sound :as s])
  )


(def audio-player (atom nil))

(def sounds {
  :thinking-music (s/read-sound "sounds/thinking.mp3")
  :buzz (s/read-sound "sounds/buzz.mp3")
  :correct (s/read-sound "sounds/correct.mp3")
  :error (s/read-sound "sounds/error.mp3")
  :t1 (s/read-sound "sounds/p1.mp3")
  :t2 (s/read-sound "sounds/p2.mp3")
  :t3 (s/read-sound "sounds/p3.mp3")
  :t4 (s/read-sound "sounds/p4.mp3")
  :ping (s/read-sound "sounds/ping.mp3")
  :timeout (s/read-sound "sounds/timeout.mp3")
})

(defn play
  [sound]
  (if-let [s (sound sounds)]
    (reset! audio-player {:sound sound
                          :playing (s/play s)})))

(defn stop
  []
  (when @audio-player (s/stop (:playing @audio-player))
  (reset! audio-player nil)))

(defn playing
  []
  (if (or (nil? @audio-player) (future-done? (get-in @audio-player [:playing :player])))
    nil
    (:sound @audio-player)))

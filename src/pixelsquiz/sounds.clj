(ns pixelsquiz.sounds
  (:gen-class)
  (:require [dynne.sampled-sound :as s])
  )


(def audio-players (atom []))

(def sounds {
  :thinking-music (s/read-sound "sounds/thinking.mp3")
  :buzz (s/read-sound "sounds/buzz.mp3")
  :correct (s/read-sound "sounds/correct.mp3")
  :error (s/read-sound "sounds/error.mp3")
  :select-option (s/read-sound "sounds/click.mp3")
  :ping (s/read-sound "sounds/ping.mp3")
  :timeout (s/read-sound "sounds/timeout.mp3")
})

(defn play
  [sound]
  (if-let [s (sound sounds)]
    (reset! audio-players (conj @audio-players {:sound sound
                                                :playing (s/play s)}))))

(defn stop
  []
  (do
    (doseq [player @audio-players] (s/stop (:playing player)))
    (reset! audio-players [])))

(defn playing
  []
  (mapv #(:sound %) (filter #(not (future-done? (get-in % [:playing :player]))) @audio-players)))

(defn is-playing
  [sound]
  (boolean (some #{sound} (playing))))

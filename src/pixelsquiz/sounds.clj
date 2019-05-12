(ns pixelsquiz.sounds
  (:gen-class))

(require '[dynne.sampled-sound :as dynne])


(def audio-players (atom []))

(def sounds {
  :thinking-music (dynne/read-sound "sounds/thinking.mp3")
  :buzz (dynne/read-sound "sounds/buzz.mp3")
  :correct (dynne/read-sound "sounds/correct.mp3")
  :error (dynne/read-sound "sounds/error.mp3")
  :select-option (dynne/read-sound "sounds/click.mp3")
  :ping (dynne/read-sound "sounds/ping.mp3")
  :timeout (dynne/read-sound "sounds/timeout.mp3")
})

(defn play
  [sound]
  (if-let [s (sound sounds)]
    (reset! audio-players (conj @audio-players {:sound sound
                                                :playing (dynne/play s)}))))

(defn stop
  []
  (do
    (doseq [player @audio-players] (dynne/stop (:playing player)))
    (reset! audio-players [])))

(defn playing
  []
  (mapv #(:sound %) (filter #(not (future-done? (get-in % [:playing :player]))) @audio-players)))

(defn is-playing
  [sound]
  (boolean (some #{sound} (playing))))

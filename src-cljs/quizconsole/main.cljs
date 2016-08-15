(ns quizconsole.main
  (:require [dommy.core :refer [sel sel1 listen! attr]]
            )
  )

(def baseurl "//localhost:3000/")

(def conn 
  (js/WebSocket. (str "ws:" baseurl "displays")))


(defn send-command
  [el]
  (let [command (attr el :id)]
   (.open (.XMLHttpRequest (str baseurl "/actions/" command)) 
    ))

(doseq [but (children (sel [:#commands :button]))]
  (listen! but :click send-command))

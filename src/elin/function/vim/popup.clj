(ns elin.function.vim.popup
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.function.vim  :as e.f.vim]))

(defn open
  ([host s]
   (open host s {}))
  ([host s options]
   (let [texts (str/split-lines s)]
     (e.f.vim/call host "elin#internal#popup#open" [texts options]))))

(defn open!!
  ([host s]
   (open!! host s {}))
  ([host s options]
   (async/<!! (open host s options))))

(defn move
  [host winid lnum col]
  (e.f.vim/notify host "elin#internal#popup#move" [winid lnum col]))

(defn set-text
  [host winid s]
  (let [texts (str/split-lines s)]
    (e.f.vim/notify host "elin#internal#popup#set_texts" [winid texts])))

(defn close
  [host winid]
  (e.f.vim/notify host "elin#internal#popup#close" [winid]))

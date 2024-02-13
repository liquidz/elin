(ns elin.function.vim.popup
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.function.vim  :as e.f.vim]))

(defn open
  ([writer s]
   (open writer s {}))
  ([writer s options]
   (let [texts (str/split-lines s)]
     (e.f.vim/call writer "elin#internal#popup#open" [texts options]))))

(defn open!!
  ([writer s]
   (open!! writer s {}))
  ([writer s options]
   (async/<!! (open writer s options))))

(defn move
  [writer winid lnum col]
  (e.f.vim/notify writer "elin#internal#popup#move" [winid lnum col]))

(defn set-text
  [writer winid s]
  (let [texts (str/split-lines s)]
    (e.f.vim/notify writer "elin#internal#popup#set_texts" [winid texts])))

(defn close
  [writer winid]
  (e.f.vim/notify writer "elin#internal#popup#close" [winid]))

(ns elin.component.server.impl.popup
  (:require
   [clojure.string :as str]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]))

(defn open-popup!*
  ([host s]
   (open-popup!* host s {}))
  ([host s options]
   (let [texts (str/split-lines s)]
     (e.c.s.function/request! host "elin#internal#popup#open" [texts options]))))

(defn move-popup*
  [host winid lnum col]
  (e.c.s.function/notify host "elin#internal#popup#move" [winid lnum col]))

(defn set-popup-text*
  [host winid s]
  (let [texts (str/split-lines s)]
    (e.c.s.function/notify host "elin#internal#popup#set_texts" [winid texts])))

(defn close-popup*
  [host winid]
  (e.c.s.function/notify host "elin#internal#popup#close" [winid]))

(extend-protocol e.p.host/IPopup
  elin.component.server.vim.VimHost
  (open-popup!
    ([this s] (open-popup!* this s))
    ([this s options] (open-popup!* this s options)))
  (move-popup [this popup-id lnum col]
    (move-popup* this popup-id lnum col))
  (set-popup-text [this popup-id s]
    (set-popup-text* this popup-id s))
  (close-popup [this popup-id]
    (close-popup* this popup-id))

  elin.component.server.nvim.NvimHost
  (open-popup!
    ([this s] (open-popup!* this s))
    ([this s options] (open-popup!* this s options)))
  (move-popup [this popup-id lnum col]
    (move-popup* this popup-id lnum col))
  (set-popup-text [this popup-id s]
    (set-popup-text* this popup-id s))
  (close-popup [this popup-id]
    (close-popup* this popup-id)))

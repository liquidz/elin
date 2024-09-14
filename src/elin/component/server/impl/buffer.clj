(ns elin.component.server.impl.buffer
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [malli.core :as m]))

(m/=> set-to-current-buffer* [:=> [:cat e.c.s.function/?IFunction string?] :nil])
(defn- set-to-current-buffer*
  [host s]
  (e.c.s.function/notify host "elin#internal#buffer#set" ["%" s]))

(m/=> append-to-info-buffer* [:=> [:cat e.c.s.function/?IFunction string? map?] :nil])
(defn- append-to-info-buffer*
  [host s options]
  (when (seq s)
    (e.c.s.function/notify host "elin#internal#buffer#info#append" [s]))

  (when (:show-temporarily? options)
    (e.c.s.function/notify host "elin#internal#buffer#temp#set" [s])))

(defn- get-lines*
  [host start-lnum end-lnum]
  (e.c.s.function/request! host "getline" [start-lnum end-lnum]))

(defn- set-highlight*
  ([host highlight-group lnum]
   (e.c.s.function/notify host "elin#internal#buffer#set_highlight" [highlight-group lnum]))
  ([host highlight-group lnum start-col end-col]
   (e.c.s.function/notify host "elin#internal#buffer#set_highlight" [highlight-group lnum start-col end-col])))

(defn- clear-highlight*
  [host]
  (e.c.s.function/notify host "elin#internal#buffer#clear_highlight" []))

(extend-protocol e.p.host/IBuffer
  elin.component.server.vim.VimHost
  (set-to-current-buffer [this text]
    (set-to-current-buffer* this text))
  (append-to-info-buffer
    ([this text]
     (append-to-info-buffer* this text {}))
    ([this text options]
     (append-to-info-buffer* this text options)))
  (get-lines
    ([this]
     (get-lines* this 1 "$"))
    ([this start-lnum]
     (get-lines* this start-lnum "$"))
    ([this start-lnum end-lnum]
     (get-lines* this start-lnum end-lnum)))
  (set-highlight
    ([this highlight-group lnum]
     (set-highlight* this highlight-group lnum))
    ([this highlight-group lnum start-col end-col]
     (set-highlight* this highlight-group lnum start-col end-col)))
  (clear-highlight [this]
    (clear-highlight* this))

  elin.component.server.nvim.NvimHost
  (set-to-current-buffer [this text]
    (set-to-current-buffer* this text))
  (append-to-info-buffer
    ([this text]
     (append-to-info-buffer* this text {}))
    ([this text options]
     (append-to-info-buffer* this text options)))
  (get-lines
    ([this]
     (get-lines* this 1 "$"))
    ([this start-lnum]
     (get-lines* this start-lnum "$"))
    ([this start-lnum end-lnum]
     (get-lines* this start-lnum end-lnum)))
  (set-highlight
    ([this highlight-group lnum]
     (set-highlight* this highlight-group lnum))
    ([this highlight-group lnum start-col end-col]
     (set-highlight* this highlight-group lnum start-col end-col)))
  (clear-highlight [this]
    (clear-highlight* this)))

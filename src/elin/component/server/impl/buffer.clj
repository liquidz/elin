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

(m/=> append-to-info-buffer* [:=> [:cat e.c.s.function/?IFunction string?] :nil])
(defn- append-to-info-buffer*
  [host s]
  (when (seq s)
    (e.c.s.function/notify host "elin#internal#buffer#info#append" [s])))

(extend-protocol e.p.host/IBuffer
  elin.component.server.vim.VimHost
  (set-to-current-buffer [this text]
    (set-to-current-buffer* this text))
  (append-to-info-buffer [this text]
    (append-to-info-buffer* this text))

  elin.component.server.nvim.NvimHost
  (set-to-current-buffer [this text]
    (set-to-current-buffer* this text))
  (append-to-info-buffer [this text]
    (append-to-info-buffer* this text)))

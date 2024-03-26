(ns elin.component.server.impl.info-buffer
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> append-to-info-buffer* [:=> [:cat e.s.server/?Host string?] :nil])
(defn- append-to-info-buffer*
  [host s]
  (when (seq s)
    (e.c.s.function/notify host "elin#internal#buffer#info#append" [s])))

(extend-protocol e.p.host/IInfoBuffer
  elin.component.server.vim.VimHost
  (append-to-info-buffer [this text]
    (append-to-info-buffer* this text))

  elin.component.server.nvim.NvimHost
  (append-to-info-buffer [this text]
    (append-to-info-buffer* this text)))

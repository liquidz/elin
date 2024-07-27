(ns elin.component.server.impl.io
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [malli.core :as m]))

(m/=> input!* [:=> [:cat e.s.component/?LazyHost string? string?] e.schema/?ManyToManyChannel])
(defn- input!*
  [host prompt default]
  (e.c.s.function/request! host "input" [prompt default]))

(extend-protocol e.p.host/IIo
  elin.component.server.vim.VimHost
  (echo-text
    ([this text]
     (e.p.host/echo-text this text "Normal"))
    ([this text highlight]
     (e.p.h.rpc/notify! this ["call" "elin#internal#echo" [text highlight]])))
  (echo-message
    ([this text]
     (e.p.host/echo-message this text "Normal"))
    ([this text highlight]
     (e.p.h.rpc/notify! this ["call" "elin#internal#echom" [text highlight]])))
  (input! [this prompt default]
    (input!* this prompt default))

  elin.component.server.nvim.NvimHost
  (echo-text
    ([this text]
     (e.p.host/echo-text this text "Normal"))
    ([this text highlight]
     (e.p.h.rpc/notify! this ["nvim_call_function" ["elin#internal#echo" [text highlight]]])))
  (echo-message
    ([this text]
     (e.p.host/echo-message this text "Normal"))
    ([this text highlight]
     (e.p.h.rpc/notify! this ["nvim_echo" [[[text highlight]] true {}]])))
  (input! [this prompt default]
    (input!* this prompt default)))

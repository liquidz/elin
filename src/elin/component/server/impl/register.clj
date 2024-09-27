(ns elin.component.server.impl.register
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [malli.core :as m]))

(m/=> yank* [:=> [:cat e.c.s.function/?IFunction string?] e.schema/?ManyToManyChannel])
(defn- yank*
  [host text]
  ;; (e.p.h.rpc/notify! host ["call" "elin#internal#register#yank_and_slide" [text]])
  (e.c.s.function/notify host "elin#internal#register#yank_and_slide" [text]))

(extend-protocol e.p.host/IRegister
  elin.component.server.vim.VimHost
  (yank [this text]
    (yank* this text))

  elin.component.server.nvim.NvimHost
  (yank [this text]
    (yank* this text)))

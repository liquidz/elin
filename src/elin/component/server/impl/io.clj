(ns elin.component.server.impl.io
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> input!* [:=> [:cat e.s.server/?Host string? string?] string?])
(defn- input!*
  [host prompt default]
  (e.c.s.function/request! host "input" [prompt default]))

(extend-protocol e.p.host/IIo
  elin.component.server.vim.VimHost
  (input! [this prompt default] (input!* this prompt default))

  elin.component.server.nvim.NvimHost
  (input! [this prompt default] (input!* this prompt default)))

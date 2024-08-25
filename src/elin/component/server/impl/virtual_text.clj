(ns elin.component.server.impl.virtual-text
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]))

(defn ^:private set-virtual-text*
  ([host text]
   (set-virtual-text* host text {}))
  ([host text options]
   (e.c.s.function/notify host "elin#internal#virtual_text#set" [text options])))

(defn clear-all-virtual-texts*
  [host]
  (e.c.s.function/notify host "elin#internal#virtual_text#clear" []))

(extend-protocol e.p.host/IVirtualText
  elin.component.server.vim.VimHost
  (set-virtual-text
    ([this text]
     (set-virtual-text* this text))
    ([this text options]
     (set-virtual-text* this text options)))
  (clear-all-virtual-texts [this]
    (clear-all-virtual-texts* this))

  elin.component.server.nvim.NvimHost
  (set-virtual-text
    ([this text]
     (set-virtual-text* this text))
    ([this text options]
     (set-virtual-text* this text options)))
  (clear-all-virtual-texts [this]
    (clear-all-virtual-texts* this)))

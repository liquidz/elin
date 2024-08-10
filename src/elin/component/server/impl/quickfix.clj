(ns elin.component.server.impl.quickfix
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.schema.host :as e.s.host]
   [malli.core :as m]))

(m/=> set-quickfix-list* [:=> [:cat
                               e.c.s.function/?IFunction
                               [:sequential e.s.host/?QuickfixListItem]]
                          :nil])
(defn set-quickfix-list*
  [host qf-list]
  (let [qf-list' (map #(update % :type (comp str first))
                      qf-list)]
    (e.c.s.function/notify host "setqflist" [qf-list' " "])))

(m/=> set-location-list* [:=> [:cat
                               e.c.s.function/?IFunction
                               int?
                               [:sequential e.s.host/?QuickfixListItem]]
                          :nil])
(defn set-location-list*
  [host window-id loc-list]
  (let [loc-list' (map #(update % :type (comp str first))
                       loc-list)]
    (e.c.s.function/notify host "setloclist" [window-id loc-list' " "])))

(extend-protocol e.p.host/IQuickfix
  elin.component.server.vim.VimHost
  (set-quickfix-list [this quickfix-list]
    (set-quickfix-list* this quickfix-list))
  (set-location-list [this window-id location-list]
    (set-location-list* this window-id location-list))

  elin.component.server.nvim.NvimHost
  (set-quickfix-list [this quickfix-list]
    (set-quickfix-list* this quickfix-list))
  (set-location-list [this window-id location-list]
    (set-location-list* this window-id location-list)))

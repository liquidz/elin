(ns elin.component.server.impl.quickfix
  (:require
   [clojure.core.async :as async]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.schema.host :as e.s.host]
   [malli.core :as m]))

(defn- initial->quickfix-type
  [c]
  (case c
    "E" "Error"
    "W" "Warning"
    "I" "Info"
    "Info"))

(m/=> get-quickfix-list* [:=> [:cat e.c.s.function/?IFunction] [:sequential e.s.host/?QuickfixListItem]])
(defn- get-quickfix-list*
  [host]
  (async/go
    (->> (e.c.s.function/request! host "elin#internal#quickfix#getqflist" [])
         (async/<!)
         (map #(-> (update-keys % keyword)
                   (select-keys [:filename :lnum :col :text :type])
                   (update :type initial->quickfix-type))))))

(m/=> set-quickfix-list* [:=> [:cat
                               e.c.s.function/?IFunction
                               [:sequential e.s.host/?QuickfixListItem]]
                          :nil])
(defn- set-quickfix-list*
  [host qf-list]
  (let [qf-list' (map #(update % :type (comp str first))
                      qf-list)]
    (e.c.s.function/notify host "setqflist" [qf-list' "r"])))

(m/=> get-location-list* [:=> [:cat e.c.s.function/?IFunction int?]
                          [:sequential e.s.host/?QuickfixListItem]])
(defn- get-location-list*
  [host window-id]
  (async/go
    (->> (e.c.s.function/request! host "elin#internal#quickfix#getloclist" [window-id])
         (async/<!)
         (map #(-> (update-keys % keyword)
                   (select-keys [:filename :lnum :col :text :type])
                   (update :type initial->quickfix-type))))))

(m/=> set-location-list* [:=> [:cat
                               e.c.s.function/?IFunction
                               int?
                               [:sequential e.s.host/?QuickfixListItem]]
                          :nil])
(defn set-location-list*
  [host window-id loc-list]
  (let [loc-list' (map #(update % :type (comp str first))
                       loc-list)]
    (e.c.s.function/notify host "setloclist" [window-id loc-list' "r"])))

(extend-protocol e.p.host/IQuickfix
  elin.component.server.vim.VimHost
  (get-quickfix-list
    [this]
    (get-quickfix-list* this))
  (set-quickfix-list [this quickfix-list]
    (set-quickfix-list* this quickfix-list))
  (get-location-list
    [this window-id]
    (get-location-list* this window-id))
  (set-location-list [this window-id location-list]
    (set-location-list* this window-id location-list))

  elin.component.server.nvim.NvimHost
  (get-quickfix-list
    [this]
    (get-quickfix-list* this))
  (set-quickfix-list [this quickfix-list]
    (set-quickfix-list* this quickfix-list))
  (get-location-list
    [this window-id]
    (get-location-list* this window-id))
  (set-location-list [this window-id location-list]
    (set-location-list* this window-id location-list)))

(ns elin.component.server.impl.variable
  (:require
   [clojure.core.async :as async]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> get-variable!* [:=> [:cat e.s.server/?Host string?] e.schema/?ManyToManyChannel])
(defn- get-variable!*
  [host var-name]
  (e.c.s.function/eval! host (format "exists('%s') ? %s : v:null" var-name var-name)))

(m/=> set-variable!* [:=> [:cat e.s.server/?Host string? any?] e.schema/?ManyToManyChannel])
(defn- set-variable!*
  [host var-name value]
  (async/go
    (let [value' (cond
                   (string? value) (str "'" value "'")
                   (true? value) "v:true"
                   (false? value) "v:false"
                   :else value)]
      (async/<! (e.c.s.function/execute! host (format "let %s = %s" var-name value')))
      nil)))

(extend-protocol e.p.host/IIo
  elin.component.server.vim.VimHost
  (get-variable! [this var-name] (get-variable!* this var-name))
  (set-variable! [this var-name value] (set-variable!* this var-name value))

  elin.component.server.nvim.NvimHost
  (get-variable! [this var-name] (get-variable!* this var-name))
  (set-variable! [this var-name value] (set-variable!* this var-name value)))

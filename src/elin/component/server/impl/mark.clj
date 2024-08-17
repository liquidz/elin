(ns elin.component.server.impl.mark
  (:require
   [clojure.core.async :as async]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [malli.core :as m]))

(m/=> get-mark* [:-> e.c.s.function/?IFunction string? e.schema/?ManyToManyChannel])
(defn- get-mark*
  [host mark-id]
  (async/go
    (e/-> (e.c.s.function/request! host "elin#internal#mark#get" [mark-id])
          (async/<!)
          (update-keys keyword))))

(extend-protocol e.p.host/IMark
  elin.component.server.vim.VimHost
  (get-mark [this mark-id]
    (get-mark* this mark-id))

  elin.component.server.nvim.NvimHost
  (get-mark [this mark-id]
    (get-mark* this mark-id)))

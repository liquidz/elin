(ns elin.component.server.impl.select
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.protocol.host :as e.p.host]))

(defn- select-from-candidates*
  [host candidates callback-handler-symbol & [optional-params]]
  (let [args (cond-> [candidates callback-handler-symbol]
               optional-params
               (concat [optional-params]))]
    (e.c.s.function/notify host "elin#internal#select" args)))

(extend-protocol e.p.host/ISelector
  elin.component.server.vim.VimHost
  (select-from-candidates
    ([this candidates callback-handler-symbol]
     (select-from-candidates* this candidates callback-handler-symbol))
    ([this candidates callback-handler-symbol optional-params]
     (select-from-candidates* this candidates callback-handler-symbol optional-params)))

  elin.component.server.nvim.NvimHost
  (select-from-candidates
    ([this candidates callback-handler-symbol]
     (select-from-candidates* this candidates callback-handler-symbol))
    ([this candidates callback-handler-symbol optional-params]
     (select-from-candidates* this candidates callback-handler-symbol optional-params))))

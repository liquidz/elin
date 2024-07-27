(ns elin.component.server.impl.function
  (:require
   [clojure.core.async :as async]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.error :as e]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [elin.util.id :as e.u.id]
   [elin.util.server :as e.u.server]
   [malli.core :as m]))

(defprotocol IFunction
  (request-function [this method params])
  (notify-function [this method params]))

(extend-protocol IFunction
  elin.component.server.vim.VimHost
  (request-function [this method params]
    (e.p.h.rpc/request! this ["call" method params (e.u.id/next-id)]))

  (notify-function [this method params]
    (e.p.h.rpc/notify! this ["call" method params]))

  elin.component.server.nvim.NvimHost
  (request-function [this method params]
    (e.p.h.rpc/request! this ["nvim_call_function" [method params]]))

  (notify-function [this method params]
    (e.p.h.rpc/notify! this ["nvim_call_function" [method params]])))

(m/=> request! [:=>
                [:cat e.s.component/?LazyHost string? [:sequential any?]]
                e.schema/?ManyToManyChannel])
(defn request!
  [host fn-name params]
  (async/go
    (let [{:keys [result error]} (->> (e.u.server/format params)
                                      (request-function host fn-name)
                                      (async/<!))]
      (if error
        (e/fault {:message (str "Failed to call function: " error)
                  :function fn-name
                  :params params})
        result))))

(m/=> notify [:=> [:cat e.s.component/?LazyHost string? [:sequential any?]] :nil])
(defn notify
  [host fn-name params]
  (->> (map e.u.server/format params)
       (notify-function host fn-name))
  nil)

(m/=> execute! [:=> [:cat e.s.component/?LazyHost string?] e.schema/?ManyToManyChannel])
(defn execute!
  [host cmd]
  (request! host "elin#internal#execute" [cmd]))

(m/=> eval! [:=> [:cat e.s.component/?LazyHost string?] e.schema/?ManyToManyChannel])
(defn eval!
  [host s]
  (request! host "elin#internal#eval" [s]))

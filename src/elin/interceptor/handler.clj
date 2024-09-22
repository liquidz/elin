(ns elin.interceptor.handler
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(def handling-error-interceptor
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (e.message/error host (ex-message response)))
              (ix/when (comp e/error? :response))
              (ix/discard))})

(def setting-nrepl-connection-status-interceptor
  "Interceptor for setting nREPL connection status to variable."
  (let [status-handler? #(= :elin.handler.internal/status
                            (get-in % [:message :method]))]
    {:kind e.c.interceptor/handler
     :params [""]
     :enter (-> (fn [ctx]
                  (let [{:keys [params]} (e.u.interceptor/self ctx)
                        variable-name (first params)]
                    (assoc ctx ::variable-name variable-name)))
                (ix/when status-handler?))
     :leave (-> (fn [{:component/keys [host] ::keys [variable-name] :keys [response]}]
                  (when (and (string? variable-name)
                             (string? response)
                             (seq variable-name)
                             (seq response))
                    (e.p.host/set-variable! host variable-name response)))
                (ix/when status-handler?)
                (ix/discard))}))

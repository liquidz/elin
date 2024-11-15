(ns elin.interceptor.handler.namespace
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [exoscale.interceptor :as ix]))

(def show-result
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (let [{result :result target-symbol :target alias-symbol :alias} response]
                  (cond
                    (not result)
                    (e.message/warning host (format "'%s' already exists." target-symbol))

                    (and target-symbol alias-symbol)
                    (e.message/info host (format "'%s' added as '%s'."
                                                 target-symbol alias-symbol))

                    :else
                    (e.message/info host (format "'%s' added."
                                                 target-symbol)))))
              (ix/discard))})

(def yank-alias
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (let [{result :result alias-symbol :alias} response]
                  (when (and result alias-symbol)
                    (e.p.host/yank host (str alias-symbol)))))
              (ix/discard))})

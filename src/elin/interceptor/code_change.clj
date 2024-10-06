(ns elin.interceptor.code-change
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [exoscale.interceptor :as ix]))

(def code-changed-result
  {:kind e.c.interceptor/code-change
   :leave (-> (fn [{:as ctx :component/keys [host] :keys [target response]}]
                (condp contains? (:type ctx)
                  #{:add-missing-require :add-libspec}
                  (let [{:keys [namespace-symbol alias-symbol]} target]
                    (if response
                      (e.message/info host (if alias-symbol
                                             (format "'%s' added as '%s'."
                                                     namespace-symbol alias-symbol)
                                             (format "'%s' added."
                                                     namespace-symbol)))
                      (e.message/warning host (format "'%s' already exists." namespace-symbol))))

                  #{:add-missing-import}
                  (e.message/info host (format "'%s' added." (:class-name-symbol target)))

                  nil))
              (ix/discard))})

(def yank-added-alias
  {:kind e.c.interceptor/code-change
   :leave (-> (fn [{:component/keys [host] :keys [target]}]
                (e.p.host/yank host (str (:alias-symbol target))))
              (ix/when #(and (= :add-libspec (:type %))
                             (:alias-symbol (:target %))))
              (ix/discard))})

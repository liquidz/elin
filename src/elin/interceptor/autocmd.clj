(ns elin.interceptor.autocmd
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.host :as e.f.host]
   [elin.function.nrepl.op :as e.f.n.op]
   [elin.function.sexp :as e.f.sexp]
   [elin.protocol.nrepl :as e.p.nrepl]))

(def ^:priavte ns-created-var-name
  "b:elin_ns_created")

(def ns-create-interceptor
  {:name ::ns-create-interceptor
   :kind e.c.interceptor/autocmd
   :enter (fn [{:as ctx :keys [elin autocmd-type]}]
            (let [{:component/keys [writer nrepl]} elin]
              (when (and (contains? #{"BufRead" "BufEnter"} autocmd-type)
                         (not (e.p.nrepl/disconnected? nrepl))
                         (nil? (e.f.host/get-variable writer ns-created-var-name)))
                (when-let [ns-sym (some-> (e.f.sexp/get-namespace writer)
                                          (symbol))]
                  (->> `(when-not (clojure.core/find-ns '~ns-sym)
                          (clojure.core/create-ns '~ns-sym))
                       (str)
                       (e.f.n.op/eval nrepl)
                       (async/<!!))
                  (e.f.host/set-variable writer ns-created-var-name true))))
            ctx)})

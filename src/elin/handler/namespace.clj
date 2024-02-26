(ns elin.handler.namespace
  (:require
   [elin.error :as e]
   [elin.function.core.namespace :as e.f.c.namespace]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.util.sexp :as e.u.sexp]))

(defn add-namespace*
  [{:as elin :component/keys [host] :keys [message]}]
  (e/let [ns-sym (-> (:params message)
                     (first)
                     (symbol)
                     (or (e/not-found)))
          alias-sym (e.f.c.namespace/most-used-namespace-alias elin ns-sym)
          ns-form (e/-> (e.f.v.sexp/get-namespace-form!! host)
                        (e.u.sexp/add-require ns-sym alias-sym))]
    (e.f.v.sexp/replace-namespace-form!! host ns-form)))

(defn add-namespace
  [{:as elin :component/keys [host]}]
  (let [coll (e.f.c.namespace/get-namespaces elin)]
    (e.f.vim/notify host "elin#internal#select" [coll (symbol #'add-namespace*)])))

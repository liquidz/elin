(ns elin.handler.namespace
  (:require
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.core.namespace :as e.f.c.namespace]
   [elin.function.nrepl.vim :as e.f.n.vim]
   [elin.function.vim :as e.f.vim]
   [elin.function.vim.sexp :as e.f.v.sexp]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.sexp :as e.u.sexp]))

(defn- has-namespace?
  [form ns-sym]
  (-> (re-pattern (str (str/replace (str ns-sym) "." "\\.")
                       "[ \r\n\t\\]\\)]"))
      (re-seq form)
      (some?)))

(defn add-namespace*
  [{:as elin :component/keys [host nrepl] :keys [message]}]
  (e/let [ns-sym (-> (:params message)
                     (first)
                     (symbol)
                     (or (e/not-found)))
          alias-sym (e.f.c.namespace/most-used-namespace-alias elin ns-sym)
          ns-form (e.f.v.sexp/get-namespace-form!! host)]
    (if (has-namespace? ns-form ns-sym)
      (e.p.rpc/echo-text host (format "'%s' already exists." ns-sym) "WarningMsg")
      (e/let [ns-form' (e.u.sexp/add-require ns-form ns-sym alias-sym)]
        (e.f.v.sexp/replace-namespace-form!! host ns-form')
        (e.f.n.vim/evaluate-namespace-form!! {:host host :nrepl nrepl})
        (e.p.rpc/echo-text host (format "'%s' added." ns-sym) "MoreMsg")))))

(defn add-namespace
  [{:as elin :component/keys [host]}]
  (let [coll (e.f.c.namespace/get-namespaces elin)]
    (e.f.vim/notify host "elin#internal#select" [coll (symbol #'add-namespace*)])))

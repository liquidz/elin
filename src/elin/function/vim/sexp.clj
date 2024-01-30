(ns elin.function.vim.sexp
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e.error]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [elin.util.function :as e.u.function]
   [malli.core :as m]
   [rewrite-clj.zip :as r.zip]))

(m/=> get-top-list!! [:=> [:cat e.s.server/?Writer int? int?] (e.schema/error-or string?)])
(defn get-top-list!!
  [writer lnum col]
  (async/<!! (e.u.function/call-function writer "elin#compat#sexp#get_top_list" [lnum col])))

(m/=> get-list!! [:=> [:cat e.s.server/?Writer int? int?] (e.schema/error-or string?)])
(defn get-list!!
  [writer lnum col]
  (async/<!! (e.u.function/call-function writer "elin#compat#sexp#get_list" [lnum col])))

(m/=> get-expr!! [:=> [:cat e.s.server/?Writer int? int?] (e.schema/error-or string?)])
(defn get-expr!!
  [writer lnum col]
  (async/<!! (e.u.function/call-function writer "elin#compat#sexp#get_expr" [lnum col])))

(m/=> get-namespace!! [:=> [:cat e.s.server/?Writer] (e.schema/error-or [:maybe string?])])
(defn get-namespace!!
  [writer]
  (try
    (e.error/let [ns-form (async/<!! (e.u.function/call-function writer "elin#internal#clojure#get_ns_form" []))
                  target-sym (if (str/includes? ns-form "in-ns") 'in-ns 'ns)]
      (when (seq ns-form)
        (-> ns-form
            (r.zip/of-string)
            (r.zip/find-value r.zip/next target-sym)
            (r.zip/right)
            (as-> zloc
              (if (= :quote (r.zip/tag zloc))
                (r.zip/down zloc)
                zloc))
            (r.zip/sexpr)
            (str))))
    (catch Exception ex
      (e.error/not-found {:message (ex-message ex)}))))

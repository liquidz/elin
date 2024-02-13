(ns elin.function.vim.sexp
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.vim  :as e.f.vim]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]
   [rewrite-clj.zip :as r.zip]))

(def ^:private ?CodeAndPosition
  [:map
   [:code string?]
   [:lnum int?]
   [:col int?]])

(m/=> get-top-list!! [:=> [:cat e.s.server/?Writer int? int?] (e.schema/error-or ?CodeAndPosition)])
(defn get-top-list!!
  [writer lnum col]
  (-> (e.f.vim/call writer "elin#internal#sexp#get_top_list" [lnum col])
      (async/<!!)
      (update-keys keyword)))

(m/=> get-list!! [:=> [:cat e.s.server/?Writer int? int?] (e.schema/error-or ?CodeAndPosition)])
(defn get-list!!
  [writer lnum col]
  (-> (e.f.vim/call writer "elin#internal#sexp#get_list" [lnum col])
      (async/<!!)
      (update-keys keyword)))

(m/=> get-expr!! [:=> [:cat e.s.server/?Writer int? int?] (e.schema/error-or ?CodeAndPosition)])
(defn get-expr!!
  [writer lnum col]
  (-> (e.f.vim/call writer "elin#internal#sexp#get_expr" [lnum col])
      (async/<!!)
      (update-keys keyword)))

(m/=> get-namespace!! [:=> [:cat e.s.server/?Writer] (e.schema/error-or [:maybe string?])])
(defn get-namespace!!
  [writer]
  (try
    (e/let [ns-form (async/<!! (e.f.vim/call writer "elin#internal#clojure#get_ns_form" []))
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
      (e/not-found {:message (ex-message ex)}))))

(ns elin.function.vim.sexp
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.function.vim  :as e.f.vim]
   [elin.schema :as e.schema]
   [elin.schema.server :as e.s.server]
   [elin.util.sexp :as e.u.sexp]
   [malli.core :as m]))

(def ^:private ?CodeAndPosition
  [:map
   [:code string?]
   [:lnum int?]
   [:col int?]])

(m/=> get-top-list!! [:=> [:cat e.s.server/?Host int? int?] (e.schema/error-or ?CodeAndPosition)])
(defn get-top-list!!
  [host lnum col]
  (-> (e.f.vim/call host "elin#internal#sexp#get_top_list" [lnum col])
      (async/<!!)
      (update-keys keyword)))

(m/=> get-list!! [:=> [:cat e.s.server/?Host int? int?] (e.schema/error-or ?CodeAndPosition)])
(defn get-list!!
  [host lnum col]
  (-> (e.f.vim/call host "elin#internal#sexp#get_list" [lnum col])
      (async/<!!)
      (update-keys keyword)))

(m/=> get-expr!! [:=> [:cat e.s.server/?Host int? int?] (e.schema/error-or ?CodeAndPosition)])
(defn get-expr!!
  [host lnum col]
  (-> (e.f.vim/call host "elin#internal#sexp#get_expr" [lnum col])
      (async/<!!)
      (update-keys keyword)))

(m/=> get-namespace!! [:=> [:cat e.s.server/?Host] (e.schema/error-or [:maybe string?])])
(defn get-namespace!!
  [host]
  (e/-> (e.f.vim/call host "elin#internal#sexp#clojure#get_ns_form" [])
        (async/<!!)
        (e.u.sexp/extract-namespace)))

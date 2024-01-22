(ns elin.function.sexp
  (:require
   [clojure.string :as str]
   [elin.schema.server :as e.s.server]
   [elin.util.function :as e.u.function]
   [malli.core :as m]
   [rewrite-clj.zip :as r.zip]))

(m/=> get-top-list [:=> [:cat e.s.server/?Writer int? int?] string?])
(defn get-top-list
  [writer lnum col]
  (e.u.function/call-function writer "elin#compat#sexp#get_top_list" [lnum col]))

(m/=> get-list [:=> [:cat e.s.server/?Writer int? int?] string?])
(defn get-list
  [writer lnum col]
  (e.u.function/call-function writer "elin#compat#sexp#get_list" [lnum col]))

(m/=> get-expr [:=> [:cat e.s.server/?Writer int? int?] string?])
(defn get-expr
  [writer lnum col]
  (e.u.function/call-function writer "elin#compat#sexp#get_expr" [lnum col]))


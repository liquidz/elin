(ns elin.function.sexpr
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.host :as e.s.host]
   [elin.util.sexpr :as e.u.sexpr]
   [malli.core :as m]))

(m/=> get-top-list [:=> [:cat e.s.handler/?Elin int? int?] (e.schema/error-or e.s.host/?CodeAndPosition)])
(defn get-top-list
  [{:component/keys [host]} lnum col]
  (async/<!! (e.p.host/get-top-list-sexpr! host lnum col)))

(m/=> get-list [:=> [:cat e.s.handler/?Elin int? int?] (e.schema/error-or e.s.host/?CodeAndPosition)])
(defn get-list
  [{:component/keys [host]} lnum col]
  (async/<!! (e.p.host/get-list-sexpr! host lnum col)))

(m/=> get-expr [:=> [:cat e.s.handler/?Elin int? int?] (e.schema/error-or e.s.host/?CodeAndPosition)])
(defn get-expr
  [{:component/keys [host]} lnum col]
  (async/<!! (e.p.host/get-single-sexpr! host lnum col)))

(m/=> get-namespace-sexpr [:=> [:cat e.s.handler/?Elin] (e.schema/error-or e.s.host/?CodeAndPosition)])
(defn get-namespace-sexpr
  [{:component/keys [host]}]
  (async/<!! (e.p.host/get-namespace-sexpr! host)))

(m/=> replace-namespace-form [:=> [:cat e.s.handler/?Elin string?] (e.schema/error-or [:maybe string?])])
(defn replace-namespace-form
  [{:component/keys [host]} new-ns-form]
  (async/<!! (e.p.host/replace-namespace-form! host new-ns-form)))

(m/=> get-namespace [:=> [:cat e.s.handler/?Elin] (e.schema/error-or [:maybe string?])])
(defn get-namespace
  [elin]
  (e/-> (get-namespace-sexpr elin)
        (:code)
        (e.u.sexpr/extract-namespace)))

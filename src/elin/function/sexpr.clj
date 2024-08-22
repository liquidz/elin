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

(m/=> get-top-list [:function
                    [:-> e.s.handler/?Elin int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]
                    [:-> e.s.handler/?Elin string? int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-top-list
  ([{:component/keys [host]} lnum col]
   (async/<!! (e.p.host/get-top-list-sexpr! host lnum col)))
  ([{:component/keys [host]} path lnum col]
   (async/<!! (e.p.host/get-top-list-sexpr! host path lnum col))))

(m/=> get-list [:function
                [:-> e.s.handler/?Elin int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]
                [:-> e.s.handler/?Elin string? int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-list
  ([{:component/keys [host]} lnum col]
   (async/<!! (e.p.host/get-list-sexpr! host lnum col)))
  ([{:component/keys [host]} path lnum col]
   (async/<!! (e.p.host/get-list-sexpr! host path lnum col))))

(m/=> get-expr [:function
                [:-> e.s.handler/?Elin int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]
                [:-> e.s.handler/?Elin string? int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-expr
  ([{:component/keys [host]} lnum col]
   (async/<!! (e.p.host/get-single-sexpr! host lnum col)))
  ([{:component/keys [host]} path lnum col]
   (async/<!! (e.p.host/get-single-sexpr! host path lnum col))))

(m/=> get-namespace-sexpr [:function
                           [:=> [:cat e.s.handler/?Elin] (e.schema/error-or e.s.host/?CodeAndPosition)]
                           [:=> [:cat e.s.handler/?Elin string?] (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-namespace-sexpr
  ([{:component/keys [host]}]
   (async/<!! (e.p.host/get-namespace-sexpr! host)))
  ([{:component/keys [host]} path]
   (async/<!! (e.p.host/get-namespace-sexpr! host path))))

(m/=> replace-list-sexpr [:=> [:cat e.s.handler/?Elin int? int? string?] (e.schema/error-or [:maybe string?])])
(defn replace-list-sexpr
  [{:component/keys [host]} lnum col new-sexpr]
  (async/<!! (e.p.host/replace-list-sexpr! host lnum col new-sexpr)))

(m/=> get-namespace [:function
                     [:=> [:cat e.s.handler/?Elin] (e.schema/error-or [:maybe string?])]
                     [:=> [:cat e.s.handler/?Elin string?] (e.schema/error-or [:maybe string?])]])
(defn get-namespace
  ([elin]
   (e/-> (get-namespace-sexpr elin)
         (:code)
         (e.u.sexpr/extract-namespace)))
  ([elin path]
   (e/-> (get-namespace-sexpr elin path)
         (:code)
         (e.u.sexpr/extract-namespace))))

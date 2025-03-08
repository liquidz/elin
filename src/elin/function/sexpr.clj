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

(m/=> validate-code-and-position [:=> [:cat e.s.host/?CodeAndPosition] (e.schema/error-or e.s.host/?CodeAndPosition)])
(defn- validate-code-and-position
  [{:as code-and-position :keys [code]}]
  (if (seq code)
    code-and-position
    (e/not-found)))

(m/=> get-top-list [:function
                    [:-> e.s.handler/?Elin int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]
                    [:-> e.s.handler/?Elin string? int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-top-list
  ([{:component/keys [host]} lnum col]
   (e/-> (async/<!! (e.p.host/get-top-list-sexpr! host lnum col))
         (validate-code-and-position)))
  ([{:component/keys [host]} path lnum col]
   (e/-> (async/<!! (e.p.host/get-top-list-sexpr! host path lnum col))
         (validate-code-and-position))))

(m/=> get-list [:function
                [:-> e.s.handler/?Elin int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]
                [:-> e.s.handler/?Elin string? int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-list
  ([{:component/keys [host]} lnum col]
   (e/-> (async/<!! (e.p.host/get-list-sexpr! host lnum col))
         (validate-code-and-position)))
  ([{:component/keys [host]} path lnum col]
   (e/-> (async/<!! (e.p.host/get-list-sexpr! host path lnum col))
         (validate-code-and-position))))

(m/=> get-expr [:function
                [:-> e.s.handler/?Elin int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]
                [:-> e.s.handler/?Elin string? int? int? (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-expr
  ([{:component/keys [host]} lnum col]
   (e/-> (async/<!! (e.p.host/get-single-sexpr! host lnum col))
         (validate-code-and-position)))
  ([{:component/keys [host]} path lnum col]
   (e/-> (async/<!! (e.p.host/get-single-sexpr! host path lnum col))
         (validate-code-and-position))))

(m/=> get-namespace-sexpr [:function
                           [:=> [:cat e.s.handler/?Elin] (e.schema/error-or e.s.host/?CodeAndPosition)]
                           [:=> [:cat e.s.handler/?Elin string?] (e.schema/error-or e.s.host/?CodeAndPosition)]])
(defn get-namespace-sexpr
  ([{:component/keys [host]}]
   (e/-> (async/<!! (e.p.host/get-namespace-sexpr! host))
         (validate-code-and-position)))
  ([{:component/keys [host]} path]
   (e/-> (async/<!! (e.p.host/get-namespace-sexpr! host path))
         (validate-code-and-position))))

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

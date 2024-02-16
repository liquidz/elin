(ns elin.schema.component
  (:require
   [elin.schema :as e.schema]
   [elin.schema.nrepl :as e.s.nrepl]))

(def ?Atom
  (e.schema/?instance clojure.lang.Atom))

(def ?LazyHost
  [:map
   [:host-store (e.schema/?instance clojure.lang.Atom)]])

(def ?Interceptor
  [:map
   [:lazy-host ?LazyHost]
   [:interceptor-map [:map-of keyword? any?]]])

(def ^:private NreplComponent
  [:map
   [:interceptor ?Interceptor]
   [:lazy-host ?LazyHost]
   [:clients-store ?Atom]
   [:current-client-key-store ?Atom]])

(def ?Nrepl
  [:or
   NreplComponent
   e.s.nrepl/?Client])

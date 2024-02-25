(ns elin.schema.component
  (:require
   [elin.schema :as e.schema]
   [elin.schema.nrepl :as e.s.nrepl]))

(def ?LazyHost
  [:map
   [:host-store e.schema/?Atom]])

(def ?Interceptor
  [:map
   [:lazy-host ?LazyHost]
   [:interceptor-map [:map-of keyword? any?]]])

(def ^:private NreplComponent
  [:map
   [:interceptor ?Interceptor]
   [:lazy-host ?LazyHost]
   [:clients-store e.schema/?Atom]
   [:current-client-key-store e.schema/?Atom]])

(def ?Nrepl
  [:or
   NreplComponent
   e.s.nrepl/?Client])

(def ?CljKondo
  [:map
   [:lazy-host ?LazyHost]
   [:analyzing?-atom e.schema/?Atom]
   [:analyzed-atom e.schema/?Atom]])

(ns elin.schema.component
  (:require
   [elin.schema :as e.schema]))

(def ?Atom
  (e.schema/?instance clojure.lang.Atom))

(def ?Interceptor
  [:map
   [:manager ?Atom]])

(def ?Nrepl
  [:map
   [:interceptor ?Interceptor]
   [:clients-store ?Atom]
   [:current-client-key-store ?Atom]
   [:writer-store ?Atom]])

(def ?LazyWriter
  [:map
   [:writer-store (e.schema/?instance clojure.lang.Atom)]])

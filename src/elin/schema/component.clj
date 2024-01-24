(ns elin.schema.component
  (:require
   [elin.util.schema :as e.u.schema]))

(def ?Atom
  (e.u.schema/?instance clojure.lang.Atom))

(def ?Interceptor
  [:map
   [:manager ?Atom]])

(def ?Nrepl
  [:map
   [:interceptor ?Interceptor]
   [:clients-store ?Atom]
   [:current-client-key-store ?Atom]
   [:writer-store ?Atom]])

(ns elin.schema.server
  (:require
   [elin.util.schema :as e.u.schema]))

(def ?Message
  [:map
   [:host string?]
   [:message [:sequential any?]]])

(def ?Writer
  [:or
   [:map
    [:output-stream (e.u.schema/?instance java.io.OutputStream)]]
   [:map
    [:writer-store (e.u.schema/?instance clojure.lang.Atom)]]])

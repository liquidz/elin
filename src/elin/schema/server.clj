(ns elin.schema.server
  (:require
   [elin.schema :as e.schema]))

(def ?Message
  [:map
   [:host string?]
   [:message [:sequential any?]]])

(def ?Writer
  [:or
   [:map
    [:output-stream (e.schema/?instance java.io.OutputStream)]]
   [:map
    [:writer-store (e.schema/?instance clojure.lang.Atom)]]])

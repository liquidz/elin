(ns elin.schema.server
  (:require
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]))

(def ?Message
  [:map
   [:host string?]
   [:message [:sequential any?]]])

(def ?Host
  [:or
   [:map
    [:output-stream (e.schema/?instance java.io.OutputStream)]]
   e.s.component/?LazyHost])

(ns elin.schema.server)

(def ?Message
  [:map
   [:host string?]
   [:message [:sequential any?]]])

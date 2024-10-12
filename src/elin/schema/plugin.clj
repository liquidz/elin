(ns elin.schema.plugin)

(def ?Plugin
  [:map
   [:name string?]
   [:export {:optional true} [:maybe map?]]])

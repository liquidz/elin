(ns elin.schema.plugin)

(def ?Plugin
  [:map
   [:name string?]
   [:handlers {:optional true} [:sequential qualified-symbol?]]
   [:interceptors {:optional true} [:sequential qualified-symbol?]]])

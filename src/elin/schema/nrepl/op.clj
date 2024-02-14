(ns elin.schema.nrepl.op)

(def ?Lookup
  [:map
   [:ns string?]
   [:name string?]
   [:file string?]
   [:arglists-str string?]
   [:column int?]
   [:line int?]
   [:doc {:optional true} string?]
   ;; cider-nrepl's info op
   [:arglists {:optional true} [:maybe string?]]])

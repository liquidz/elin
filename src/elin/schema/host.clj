(ns elin.schema.host)

(def ?Position
  [:map
   [:bufname int?]
   [:lnum int?]
   [:col int?]
   [:off int?]
   [:curswant int?]])

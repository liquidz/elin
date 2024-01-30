(ns elin.schema.vim)

(def ?Position
  [:map
   [:bufname int?]
   [:lnum int?]
   [:col int?]
   [:off int?]
   [:curswant int?]])

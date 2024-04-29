(ns elin.schema.host)

(def ?Position
  [:map
   [:bufname int?]
   [:lnum int?]
   [:col int?]
   [:off int?]
   [:curswant int?]])

(def ?CodeAndPosition
  [:map
   [:code string?]
   [:lnum int?]
   [:col int?]])

(def ?QuickfixListItem
  [:map
   [:filename string?]
   [:lnum int?]
   [:col {:optional true} int?]
   [:text string?]
   [:type {:optional true} [:enum "Error" "Warning" "Info"]]])

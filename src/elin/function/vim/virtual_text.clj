(ns elin.function.vim.virtual-text
  (:refer-clojure :exclude [set])
  (:require
   [elin.function.vim  :as e.f.vim]))

(defn set
  ([host text]
   (set host text {}))
  ([host text options]
   (let [text (str "=> " text)]
     (e.f.vim/notify host "elin#internal#virtual_text#set" [text options]))))

(defn clear-all
  [host]
  (e.f.vim/notify host "elin#internal#virtual_text#clear" []))

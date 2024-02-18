(ns elin.function.vim.info-buffer
  (:require
   [elin.function.vim :as e.f.vim]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> append [:=> [:cat e.s.server/?Host string?] :nil])
(defn append
  [host s]
  (when (seq s)
    (e.f.vim/notify host "elin#internal#buffer#info#append" [s])))

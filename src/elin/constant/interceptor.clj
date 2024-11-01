(ns elin.constant.interceptor
  "Constants for interceptor kinds"
  (:refer-clojure :exclude [test]))

(def all ::all)
(def autocmd ::autocmd)
(def connect ::connect)
(def disconnect ::disconnect)
(def evaluate ::evaluate)
(def handler ::handler)
(def nrepl ::nrepl)
(def output ::output)
(def raw-nrepl ::raw-nrepl)
(def test ::test)
(def test-result ::test-result)
(def quickfix ::quickfix)
(def debug ::debug)
(def code-change ::code-change)

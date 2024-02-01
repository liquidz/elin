(ns elin.interceptor.output
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim :as e.f.vim]))

(def print-output-interceptor
  {:name ::print-output-interceptor
   :kind e.c.interceptor/output
   :enter (fn [{:as ctx :keys [writer output]}]
            (e.f.vim/call!! writer "elin#internal#buffer#info#append" [(pr-str output)])
            ctx)})

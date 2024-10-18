(ns elin.handler.callback
  (:require
   [elin.function.callback :as e.f.callback]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(m/=> callback [:=> [:cat e.s.handler/?Elin] boolean?])
(defn callback
  [{:as elin :keys [message]}]
  (when-let [[id result] (:params message)]
    (e.f.callback/callback elin id result))
  true)

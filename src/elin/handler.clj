(ns elin.handler)

(defmulti handler* (comp :method :message))
(defmethod handler* :default [_] nil)

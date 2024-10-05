(ns elin.util.handler)

(defn config
  [elin handler-var]
  (get-in elin [:component/handler :config-map (symbol handler-var)]))

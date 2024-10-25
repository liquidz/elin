(ns elin.util.handler
  (:require
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(defn config
  [elin handler-var]
  (get-in elin [:component/handler :config-map (symbol handler-var)]))

(m/=> jump-to-file-response [:function
                             [:-> string? e.s.handler/?JumpToFile]
                             [:-> string? int? e.s.handler/?JumpToFile]
                             [:-> string? int? int? e.s.handler/?JumpToFile]])
(defn jump-to-file-response
  ([path]
   (jump-to-file-response path -1 -1))
  ([path lnum]
   (jump-to-file-response path lnum -1))
  ([path lnum col]
   {:path path :lnum lnum :col col}))

(ns elin.function.popup
  (:require
   [clojure.core.async :as async]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(def ^:private default-popup-option
  {:line "near-cursor"
   :border []
   :filetype "help"})

;; popup option
;;   :line (top, bottom, near-cursor)
;;   :col (right, near-cursor)
;;   :style
;;   :border
;;   :wrap
;;   :moved (any, current-line)

(m/=> open [:function
            [:-> e.s.handler/?Elin string? int?]
            [:-> e.s.handler/?Elin string? map? int?]])
(defn open
  ([elin s]
   (open elin s default-popup-option))
  ([{:component/keys [host]} s option]
   (async/<!!
     (e.p.host/open-popup!
       host
       s
       (merge default-popup-option option)))))

(m/=> close [:-> e.s.handler/?Elin int? :nil])
(defn close
  [{:component/keys [host]} popup-id]
  (e.p.host/close-popup host popup-id)
  nil)

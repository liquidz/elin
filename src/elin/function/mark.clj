(ns elin.function.mark
  (:require
   [clojure.core.async :as async]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.host :as e.s.host]
   [malli.core :as m]))

(m/=> get-by-id [:-> e.s.handler/?Elin string? (e.schema/error-or e.s.host/?Position)])
(defn get-by-id
  [{:component/keys [host]} mark-id]
  (async/<!! (e.p.host/get-mark host mark-id)))

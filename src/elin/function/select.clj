(ns elin.function.select
  (:require
   [clojure.core.async :as async]
   [elin.function.callback :as e.f.callback]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(m/=> select-from-candidates [:=> [:cat e.s.handler/?Elin [:sequential any?]] any?])
(defn select-from-candidates
  [{:as elin :component/keys [host]} candidates]
  (let [[id ch] (e.f.callback/register elin)]
    (e.p.host/select-from-candidates host candidates 'elin.handler.callback/callback [id])
    (async/<!! ch)))

(ns elin.function.storage.test
  (:require
   [elin.protocol.storage :as e.p.storage]
   [elin.schema.handler :as e.s.handler]
   [malli.core :as m]))

(def ^:private last-test-query-key ::last-test-query)

(m/=> set-last-test-query [:=> [:cat e.s.handler/?Elin string?] :nil])
(defn set-last-test-query
  [{:component/keys [session-storage]} query]
  (e.p.storage/set session-storage last-test-query-key query))

(m/=> get-last-test-query [:=> [:cat e.s.handler/?Elin] string?])
(defn get-last-test-query
  [{:component/keys [session-storage]}]
  (e.p.storage/get session-storage last-test-query-key))

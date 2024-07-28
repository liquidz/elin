(ns elin.function.storage.test
  (:require
   [elin.protocol.storage :as e.p.storage]
   [elin.schema.component :as e.s.component]
   [malli.core :as m]))

(def ^:private last-test-query-key ::last-test-query)
(def ^:private last-failed-tests-key ::last-failed-tests)

(m/=> set* [:=> [:cat keyword? e.s.component/?Storage string?] :nil])
(defn- set*
  [storage-key storage query]
  (e.p.storage/set storage storage-key query))

(m/=> get* [:=> [:cat keyword? e.s.component/?Storage] string?])
(defn- get*
  [storage-key storage]
  (e.p.storage/get storage storage-key))

(def set-last-test-query (partial set* last-test-query-key))
(def get-last-test-query (partial get* last-test-query-key))

(def set-last-failed-tests-query (partial set* last-failed-tests-key))
(def get-last-failed-tests-query (partial get* last-failed-tests-key))

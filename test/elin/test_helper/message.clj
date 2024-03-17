(ns elin.test-helper.message
  (:require
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.schema.server :as e.s.server]
   [elin.util.server :as e.u.server]
   [malli.core :as m]))

(def ?TestMessageOption
  [:map
   [:handler [:=> [:cat [:sequential any?]] any?]]])

(defrecord TestMessage
  [host message]
  e.p.h.rpc/IRpcMessage
  (request? [_]
    (= 0 (first message)))

  (response? [_]
    (= 1 (first message)))

  (parse-message [_]
    (condp = (first message)
      ;; request
      0 (let [[_ id method [params options]] message]
          {:id id
           :method (keyword method)
           :params params
           :options (-> (e.u.server/unformat options)
                        ;; Do not allow 'callback' option for request
                        (dissoc :callback))})
      ;; response
      1 (let [[_ id error result] message]
          {:id id
           :error error
           :result result})
      ;; notify
      2 (let [[_ method [params options]] message]
          {:method (keyword method)
           :params params
           :options (e.u.server/unformat options)})
      {}))) ; }}}

(m/=> test-message [:function
                    [:=> :cat e.s.server/?Message]
                    [:=> [:cat [:sequential any?]] e.s.server/?Message]])
(defn test-message
  ([]
   (test-message []))
  ([messages]
   (map->TestMessage {:host "test"
                      :message messages})))

(ns elin.handler.tap
  (:require
   [clojure.edn :as edn]
   [clojure.pprint :as pp]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.map :as e.u.map]
   [taoensso.timbre :as timbre]))

(defn tapped
  [{:as elin :component/keys [interceptor] :keys [message]}]
  (let [value (first (:params message))
        value' (try (edn/read-string value)
                    (catch Exception ex
                      (timbre/debug (str "Failed to read tapped value: " value) ex)))
        context (-> (e.u.map/select-keys-by-namespace elin :component)
                    (assoc :value value'))
        intercept #(e.p.interceptor/execute interceptor e.c.interceptor/tap context %)]
    (or (when value'
          (-> (fn [{:as ctx :keys [value]}]
                (assoc ctx :value-str (with-out-str
                                        (pp/pprint value))))
              (intercept)
              (:value-str)))
        "")))

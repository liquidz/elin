(ns elin.handler.debug
  (:require
   [clojure.core.async :as async]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.util.nrepl :as e.u.nrepl]
   [taoensso.timbre :as timbre]))

(defn nrepl-request
  "Request any message to nREPL server.
  This handler is for debugging."
  [{:component/keys [host nrepl] :keys [message]}]
  (when-let [request (try
                       (some->> message
                                (:params)
                                (first)
                                (read-string))
                       (catch Exception ex
                         (timbre/error "Invalid parameter" ex message)
                         nil))]
    (e/-> (e.p.nrepl/request nrepl request)
          (async/<!!)
          (e.u.nrepl/merge-messages)
          (pr-str)
          (->> (e.p.host/echo-message host)))))

(defn- information-buffer-appender
  [{:component/keys [host]}]
  {:enabled? true
   :fn (fn [{:keys [output_]}]
         (e.p.host/append-to-info-buffer host (force output_)))})

(defn enable-debug-log
  [elin]
  (timbre/swap-config! #(update % :appenders assoc :information-buffer (information-buffer-appender elin))))

(defn disable-debug-log
  [_]
  (timbre/swap-config! #(update % :appenders dissoc :information-buffer)))

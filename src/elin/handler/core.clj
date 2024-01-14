(ns elin.handler.core
  (:require
   [clojure.core.async :as async]
   [elin.log :as e.log]
   [elin.nrepl.client.manager :as e.n.c.manager]
   [elin.nrepl.protocol :as e.n.protocol]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.interceptor :as e.u.interceptor]
   [malli.core :as m]))

(def client-manager
  (e.n.c.manager/new-manager))

(defn- echo* [req-map s & [hl]]
  (let [hl (or hl "Normal")]
    (e.p.rpc/notify! req-map "nvim_echo" [[[s hl]] true {}])))

(defmulti handler* :method)

(defmethod handler* :default [_] nil)

(defmethod handler* :echo
  [{:as req-map :keys [params]}]
  (echo* req-map (str "echo:" params) "ErrorMsg")
  true)

(defmethod handler* :connect
  [{:as req-map :keys [params]}]
  (let [[host port] params]
    (-> {:host host :port port}
        (e.u.interceptor/execute
         []
         (fn [{:as ctx :keys [host port]}]
           (let [client (e.n.protocol/add-client! client-manager host port)]
             ;; (echo* req-map (str "KITERUYO:" client))
             (e.n.protocol/switch-client! client-manager client)
             (assoc ctx :client client)))))
    (e.log/info "Connected")
    "Connected"))

(defmethod handler* :evaluate
  [{:keys [params]}]
  (let [[code] params
        resp (async/<!! (e.n.protocol/eval-op client-manager code {}))]
    (e.log/log "FIXME resp" resp)
    (pr-str resp)))

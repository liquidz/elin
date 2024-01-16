(ns elin.handler.core
  (:require
   [clojure.core.async :as async]
   [elin.interceptor.connect :as e.i.connect]
   [elin.log :as e.log]
   [elin.nrepl.client.manager :as e.n.c.manager]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.interceptor :as e.u.interceptor]
   [malli.core :as m]))

(def client-manager
  (e.n.c.manager/new-manager))

(defmulti handler* :method)
(defmethod handler* :default [_] nil)

(defmethod handler* :echo
  [{:as msg :keys [params]}]
  (e.log/info "FIXME echo kiteruyo" params)
  (e.p.rpc/echo-message msg (str "echo:" params) "ErrorMsg")
  true)

(defmethod handler* :connect
  [{:as msg :keys [cwd params]}]
  (let [[host port] (condp = (count params)
                      0 [nil nil]
                      1 [nil (first params)]
                      params)
        result (-> {:cwd cwd :host host :port port}
                   (e.u.interceptor/execute
                    [e.i.connect/port-auto-detecting-interceptor]
                    (fn [{:as ctx :keys [host port]}]
                      (if (and host port)
                        (let [client (e.p.nrepl/add-client! client-manager host port)]
                          (e.p.nrepl/switch-client! client-manager client)
                          (assoc ctx :client client))
                        ctx))))]
    (if (contains? result :client)
      (e.log/info msg (format "Connected to %s:%s" (:host result) (:port result)))
      (e.log/warning msg "Host or port is not specified." (pr-str (select-keys result [:host :port]))))))

(defmethod handler* :evaluate
  [{:keys [params]}]
  (let [[code] params
        resp (async/<!! (e.p.nrepl/eval-op client-manager code {}))]
    (e.log/log "FIXME resp" resp)
    (pr-str resp)))

(defmethod handler* :plus
  [{:as msg :keys [params]}]
  (let [res (async/<!! (e.p.rpc/call-function msg "elin#plus_test" params))]
    (e.log/info msg "FIXME plus result" (pr-str res))))

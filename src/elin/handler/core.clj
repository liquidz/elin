(ns elin.handler.core
  (:require
   [clojure.core.async :as async]
   [elin.function.host :as e.f.host]
   [elin.function.sexp :as e.f.sexp]
   [elin.interceptor.connect :as e.i.connect]
   [elin.log :as e.log]
   [elin.nrepl.message :as e.n.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.interceptor :as e.u.interceptor]
   [malli.core :as m]))

(defmulti handler* (comp :method :message))
(defmethod handler* :default [_] nil)

(defmethod handler* :connect
  [{:keys [nrepl message]}]
  (let [{:keys [client-manager]} nrepl
        {:keys [params]} message
        [host port] (condp = (count params)
                      0 [nil nil]
                      1 [nil (first params)]
                      params)
        cwd (e.f.host/get-current-working-directory message)
        result (-> {:message message :cwd cwd :host host :port port}
                   (e.u.interceptor/execute
                    [e.i.connect/port-auto-detecting-interceptor
                     e.i.connect/output-channel-interceptor]
                    (fn [{:as ctx :keys [host port]}]
                      (if (and host port)
                        (let [client (e.p.nrepl/add-client! client-manager host port)]
                          (e.p.nrepl/switch-client! client-manager client)
                          (assoc ctx :client client))
                        ctx))))]
    (if (contains? result :client)
      (e.log/info message (format "Connected to %s:%s" (:host result) (:port result)))
      (e.log/warning message "Host or port is not specified." (pr-str (select-keys result [:host :port]))))))

(defn- evaluation* [nrepl code & [options]]
  (-> {:code code :options (or options {})}
      (e.u.interceptor/execute
       []
       (fn [{:as ctx :keys [code options]}]
         (let [{:keys [client-manager]} nrepl
               resp (async/<!! (e.p.nrepl/eval-op client-manager code options))
               resp (e.n.message/merge-messages resp)]
           (assoc ctx :response resp))))
      (get-in [:response :value])))

(defmethod handler* :evaluate
  [{:keys [nrepl message]}]
  (->> message
       (:params)
       (first)
       (evaluation* nrepl)))

(defmethod handler* :plus
  [{:as msg :keys [params]}]
  (let [res (async/<!! (e.p.rpc/call-function msg "elin#plus_test" params))]
    (e.log/info msg "FIXME plus result" (pr-str res))))

(defmethod handler* :evaluate-current-top-list
  [{:keys [nrepl message]}]
  (let [{:keys [lnum col]} (e.f.host/get-cursor-position message)
        code (e.f.sexp/get-current-top-list message lnum col)]
    (evaluation* nrepl code)))

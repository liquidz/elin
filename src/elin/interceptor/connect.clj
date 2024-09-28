(ns elin.interceptor.connect
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.jack-in :as e.f.jack-in]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.file :as e.u.file]
   [elin.util.map :as e.u.map]
   [elin.util.process :as e.u.process]
   [exoscale.interceptor :as ix]))

(def ^:private default-hostname "localhost")

(def port-auto-detecting-interceptor
  {:kind e.c.interceptor/connect
   :enter (fn [{:as ctx :component/keys [host] :keys [hostname port]}]
            (cond
              (and hostname port)
              ctx

              (and (not hostname) port)
              (assoc ctx :hostname default-hostname)

              :else
              (if (and hostname port)
                ctx
                (let [;; TODO error handling
                      cwd (async/<!! (e.p.host/get-current-working-directory! host))
                      nrepl-port-file (e.u.file/find-file-in-parent-directories cwd ".nrepl-port")
                      hostname' (or hostname default-hostname)
                      port' (some-> nrepl-port-file
                                    (slurp)
                                    (Long/parseLong))]
                  (assoc ctx :hostname hostname' :port port')))))})

(def raw-message-channel-interceptor
  {:kind e.c.interceptor/connect
   :leave (-> (fn [{:as ctx :component/keys [interceptor] :keys [client]}]
                (let [ch (get-in client [:connection :raw-message-channel])]
                  (async/go-loop []
                    (when-let [msg (async/<! ch)]
                      (-> ctx
                          (e.u.map/select-keys-by-namespace :component)
                          (assoc :message msg)
                          (->> (e.p.interceptor/execute interceptor e.c.interceptor/raw-nrepl)))
                      (recur)))))
              (ix/when #(:client %))
              (ix/discard))})

(def connected-interceptor
  {:kind e.c.interceptor/connect
   :leave (fn [{:as ctx :component/keys [interceptor]}]
            (-> ctx
                (e.u.map/select-keys-by-namespace :component)
                (assoc :autocmd-type "BufEnter")
                (->> (e.p.interceptor/execute interceptor e.c.interceptor/autocmd)))
            ctx)})

(def cleanup-jacked-in-process-interceptor
  {:kind e.c.interceptor/disconnect
   :leave (-> (fn [{:keys [port]}]
                (-> port
                    (e.f.jack-in/port->process-id)
                    (e.u.process/kill)))
              (ix/discard))})

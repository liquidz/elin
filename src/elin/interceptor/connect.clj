(ns elin.interceptor.connect
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.jack-in :as e.f.jack-in]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.file :as e.u.file]
   [elin.util.interceptor :as e.u.interceptor]
   [elin.util.map :as e.u.map]
   [elin.util.process :as e.u.process]
   [exoscale.interceptor :as ix]
   [malli.core :as m]))

(m/=> find-clojure-port-file [:-> string? [:maybe e.s.nrepl/?PortFile]])
(defn- find-clojure-port-file
  [cwd]
  (when-let [file (e.u.file/find-file-in-parent-directories cwd ".nrepl-port")]
    {:language "clojure"
     :port-file (.getAbsolutePath file)
     :port (some->> file slurp Long/parseLong)}))

(def detect-clojure-port
  {:kind e.c.interceptor/connect
   :enter (fn [{:as ctx :component/keys [host] :keys [hostname port]}]
            (let [{:keys [default-hostname]} (e.u.interceptor/config ctx #'detect-clojure-port)]
              (cond
                (and hostname port)
                ctx

                (and (not hostname) port)
                (assoc ctx :hostname default-hostname)

                :else
                (let [;; TODO error handling
                      cwd (async/<!! (e.p.host/get-current-working-directory! host))
                      clojure-port-file (find-clojure-port-file cwd)]
                  (if clojure-port-file
                    (assoc ctx
                           :hostname (or hostname default-hostname)
                           :port (:port clojure-port-file)
                           :language (:language clojure-port-file)
                           :port-file (:port-file clojure-port-file))
                    ctx)))))})

(def raw-message-channel
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

(def connected
  {:kind e.c.interceptor/connect
   :leave (fn [{:as ctx :component/keys [interceptor]}]
            (-> ctx
                (e.u.map/select-keys-by-namespace :component)
                (assoc :autocmd-type "BufEnter")
                (->> (e.p.interceptor/execute interceptor e.c.interceptor/autocmd)))
            ctx)})

(def cleanup-jacked-in-process
  {:kind e.c.interceptor/disconnect
   :leave (-> (fn [{:keys [port]}]
                (-> port
                    (e.f.jack-in/port->process-id)
                    (e.u.process/kill)))
              (ix/discard))})

(ns elin.interceptor.connect
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.host :as e.f.host]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.file :as e.u.file]))

(def port-auto-detecting-interceptor
  {:name ::port-auto-detecting-interceptor
   :kind e.c.interceptor/connect
   :enter (fn [{:as ctx :keys [elin host port]}]
            (if (and host port)
              ctx
              (let [{:component/keys [writer]} elin
                    cwd (e.f.host/get-current-working-directory writer)
                    nrepl-port-file (e.u.file/find-file-in-parent-directories cwd ".nrepl-port")
                    host' (or host "localhost")
                    port' (some-> nrepl-port-file
                                  (slurp)
                                  (Long/parseLong))]
                (assoc ctx :host host' :port port'))))})

(def output-channel-interceptor
  {:name ::output-channel-interceptor
   :kind e.c.interceptor/connect
   :leave (fn [{:as ctx :elin/keys [interceptor] :keys [elin client]}]
            (when client
              (async/go-loop []
                (let [{:component/keys [writer]} elin
                      ch (get-in client [:connection :output-channel])
                      output (async/<! ch)]
                  (when output
                    (->> {:writer writer :output output}
                         (e.p.interceptor/execute interceptor e.c.interceptor/output))
                    (recur)))))
            ctx)})

(ns elin.interceptor.connect
  (:require
   [clojure.core.async :as async]
   [elin.function.host :as e.f.host]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.file :as e.u.file]))

(def port-auto-detecting-interceptor
  {:name ::port-auto-detecting-interceptor
   :enter (fn [{:as ctx :keys [elin host port]}]
            (if (and host port)
              ctx
              (let [{:keys [writer]} elin
                    cwd (e.f.host/get-current-working-directory writer)
                    nrepl-port-file (e.u.file/find-file-in-parent-directories cwd ".nrepl-port")
                    host' (or host "localhost")
                    port' (some-> nrepl-port-file
                                  (slurp)
                                  (Long/parseLong))]
                (assoc ctx :host host' :port port'))))})

(def output-channel-interceptor
  {:name ::output-channel-interceptor
   :leave (fn [{:as ctx :keys [elin client]}]
            (when client
              (async/go-loop []
                (let [{:keys [writer]} elin
                      ch (get-in client [:connection :output-channel])
                      {:keys [text]} (async/<! ch)]
                  (when text
                    ;; TODO FIXME
                    (e.p.rpc/echo-message writer (str "OUTPUT: " text) "ErrorMsg")
                    (recur)))))
            ctx)})

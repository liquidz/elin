(ns elin.interceptor.connect
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.function.vim :as e.f.vim]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.util.file :as e.u.file]))

(def port-auto-detecting-interceptor
  {:name ::port-auto-detecting-interceptor
   :kind e.c.interceptor/connect
   :enter (fn [{:as ctx :keys [elin hostname port]}]
            (if (and hostname port)
              ctx
              (let [{:component/keys [host]} elin
                    ;; TODO error handling
                    cwd (e.f.vim/get-current-working-directory!! host)
                    nrepl-port-file (e.u.file/find-file-in-parent-directories cwd ".nrepl-port")
                    hostname' (or hostname "localhost")
                    port' (some-> nrepl-port-file
                                  (slurp)
                                  (Long/parseLong))]
                (assoc ctx :hostname hostname' :port port'))))})

(def output-channel-interceptor
  {:name ::output-channel-interceptor
   :kind e.c.interceptor/connect
   :leave (fn [{:as ctx :keys [elin client]}]
            (when client
              (async/go-loop []
                (let [{:component/keys [nrepl interceptor host]} elin
                      ch (get-in client [:connection :output-channel])
                      output (async/<! ch)]
                  (when output
                    (->> {:component/nrepl nrepl
                          :component/interceptor interceptor
                          :component/host host
                          :output output}
                         (e.p.interceptor/execute interceptor e.c.interceptor/output))
                    (recur)))))
            ctx)})

(def connected-interceptor
  {:name ::connected-interceptor
   :kind e.c.interceptor/connect
   :leave (fn [{:as ctx :keys [elin]}]
            (let [{:component/keys [interceptor]} elin]
              (->> {:elin elin :autocmd-type "BufEnter"}
                   (e.p.interceptor/execute interceptor e.c.interceptor/autocmd)))
            ctx)})

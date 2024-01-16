(ns elin.interceptor.connect
  (:require
   [elin.util.file :as e.u.file]))

(def port-auto-detecting-interceptor
  {:name ::port-auto-detecting-interceptor
   :enter (fn [{:as ctx :keys [cwd host port]}]
            (if (and host port)
              ctx
              (let [nrepl-port-file (e.u.file/find-file-in-parent-directories cwd ".nrepl-port")
                    host' (or host "localhost")
                    port' (some-> nrepl-port-file
                                  (slurp)
                                  (Long/parseLong))]
                (assoc ctx :host host' :port port'))))})

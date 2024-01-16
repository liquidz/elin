(ns elin.core
  (:require
   [com.stuartsierra.component :as component]
   [elin.system :as elin.system]
   [elin.log :as log]))

(defn -main
  [host port cwd]
  (log/log "elin.core args" (pr-str port) "\n\n\n")
  (let [port (Long/parseLong port)
        sys-map (elin.system/new-system {:server {:host host
                                                  :port port
                                                  :cwd cwd}})]
    (component/start-system sys-map)
    (deref (promise))))

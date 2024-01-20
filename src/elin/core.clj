(ns elin.core
  (:require
   [com.stuartsierra.component :as component]
   [elin.system :as e.system]
   [elin.log :as e.log]))

(defn -main
  [host port]
  (e.log/info "elin.core args" (pr-str port) "\n\n\n")
  (let [port (Long/parseLong port)
        sys-map (e.system/new-system {:server {:host host
                                               :port port}})]
    (component/start-system sys-map)
    (deref (promise))))

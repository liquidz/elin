(ns elin.core
  (:require
   [com.stuartsierra.component :as component]
   [elin.system :as e.system]
   [elin.log :as e.log]))

(defn -main
  [host port development-mode]
  (let [port (Long/parseLong port)
        develop? (= "true" development-mode)
        sys-map (e.system/new-system {:develop? develop?
                                      :server {:host host
                                               :port port}})]
    (when develop?
      (alter-var-root #'e.log/log-level (constantly e.log/DEBUG_LEVEL)))
    (e.log/debug "elin.core Starting server:" (pr-str port) "\n\n\n")
    (component/start-system sys-map)
    (deref (promise))))

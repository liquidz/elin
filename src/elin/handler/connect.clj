(ns elin.handler.connect
  (:require
   [elin.constant.jack-in :as e.c.jack-in]
   [elin.error :as e]
   [elin.function.connect :as e.f.connect]
   [elin.function.jack-in :as e.f.jack-in]
   [elin.message :as e.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [elin.util.param :as e.u.param]
   [malli.core :as m]))

(def ^:private ?ConnectParams
  [:or
   :catn
   [:catn
    [:port int?]]
   [:catn
    [:hostname string?]
    [:port int?]]])

(defn- connect*
  [{:as elin :component/keys [host]}
   connect-arg-map]
  (let [{:as result :keys [error hostname port]} (e.f.connect/connect elin connect-arg-map)]
    (cond
      (e/fault? error)
      (e.message/warning host (format "Host or port is not specified: %s"
                                      {:hostname hostname :port port}))

      (and (e/conflict? error)
           hostname
           port)
      (e.message/warning host (format "Already connected to %s:%s"
                                      hostname port))

      (e/error? error)
      (e.message/warning host (ex-message error))

      (contains? result :client)
      (e.message/info host (format "Connected to %s:%s" hostname port)))))

(m/=> connect [:=> [:cat e.s.handler/?Elin] any?])
(defn connect
  "Connect to nREPL server."
  [{:as elin :component/keys [host] :keys [message]}]
  (let [[{:keys [hostname port]} error] (e.u.param/parse ?ConnectParams (:params message))]
    (if error
      (e.message/error host "Invalid parameter" error)
      (connect* elin {:hostname hostname :port port}))))

(defn disconnect
  "Disconnect from nREPL server."
  [{:as elin :component/keys [host nrepl]}]
  (if-let [client (e.p.nrepl/current-client nrepl)]
    (let [{:keys [error hostname port]} (e.f.connect/disconnect elin client)]
      (cond
        (e/not-found? error)
        (e.message/warning host (format "Client is not found for %s:%s" hostname port))

        (e/fault? error)
        (e.message/warning host (format "Failed to disconnect from %s:%s" hostname port))

        :else
        (e.message/info host (format "Disconnected from %s:%s" hostname port))))

    (e.message/warning host "Not connected.")))

(defn jack-in
  "Launch nREPL server according to the project detected from the current file and connect to it."
  [{:as elin :component/keys [host]}]
  (let [port (e.f.jack-in/launch-process elin)]
    (e.message/info host (format "Wainting to connect to localhost:%s" port))
    (connect* elin {:hostname "localhost" :port port :wait? true})))

(def ^:private ?InstantParams
  [:catn
   [:project
    (apply vector :enum
           (map name e.c.jack-in/supported-project-types))]])

(defn instant
  "Launch nREPL server of the specified project and connect to it."
  [{:as elin :component/keys [host] :keys [message]}]
  (let [[{:keys [project]} error] (e.u.param/parse ?InstantParams (:params message))
        port (when-not error
               (e.f.jack-in/launch-process elin {:forced-project (keyword project)}))]
    (if error
      (e.message/error host "Invalid parameter" error)
      (do (e.message/info host (format "Wainting to connect to localhost:%s" port))
          (connect* elin {:hostname "localhost" :port port :wait? true})))))

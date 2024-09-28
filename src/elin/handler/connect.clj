(ns elin.handler.connect
  (:require
   [elin.error :as e]
   [elin.function.connect :as e.f.connect]
   [elin.function.jack-in :as e.f.jack-in]
   [elin.message :as e.message]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [elin.util.param :as e.u.param]
   [malli.core :as m]))

(def ^:private ?Params
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

      (e/conflict? error)
      (e.message/warning host (format "Already connected to %s:%s"
                                      hostname port))

      (contains? result :client)
      (e.message/info host (format "Connected to %s:%s" hostname port)))))

(m/=> connect [:=> [:cat e.s.handler/?Elin] any?])
(defn connect
  [{:as elin :component/keys [host] :keys [message]}]
  (let [[{:keys [hostname port]} error] (e.u.param/parse ?Params (:params message))]
    (if error
      (e.message/error host "Invalid parameter" error)
      (connect* elin {:hostname hostname :port port}))))

(defn disconnect
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
  [{:as elin :component/keys [host]}]
  (e.message/info host "Jacking in...")
  (let [port (e.f.jack-in/launch-process elin)]
    (e.message/info host (format "Wainting to connect to localhost:%s" port))
    (connect* elin {:hostname "localhost" :port port :wait? true})))

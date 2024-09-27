(ns elin.handler.connect
  (:require
   [elin.error :as e]
   [elin.function.connect :as e.f.connect]
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

(m/=> connect [:=> [:cat e.s.handler/?Elin] any?])
(defn connect
  [{:as elin :component/keys [host] :keys [message]}]
  (let [[{:keys [hostname port]} error] (e.u.param/parse ?Params (:params message))]
    (if error
      (e.message/error host "Invalid parameter" error)
      (let [{:as result :keys [error hostname port]} (e.f.connect/connect elin {:hostname hostname :port port})]
        (cond
          (e/fault? error)
          (e.message/warning host (format "Host or port is not specified: %s"
                                          {:hostname hostname :port port}))

          (e/conflict? error)
          (e.message/warning host (format "Already connected to %s:%s"
                                          hostname port))

          (contains? result :client)
          (e.message/info host (format "Connected to %s:%s" hostname port)))))))

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

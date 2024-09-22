(ns elin.handler.connect
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.message :as e.message]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.handler :as e.s.handler]
   [elin.util.map :as e.u.map]
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
  [{:as elin :component/keys [nrepl interceptor host] :keys [message]}]
  (let [[{:keys [hostname port]} error] (e.u.param/parse ?Params (:params message))]
    (if error
      (e.message/error host "Invalid parameter" error)
      (let [context (-> elin
                        (e.u.map/select-keys-by-namespace :component)
                        (assoc :hostname hostname
                               :port port))
            connect-fn (fn [{:as ctx :keys [hostname port]}]
                         (cond
                           (or (not hostname) (not port))
                           (assoc ctx :error (e/fault))

                           (e.p.nrepl/get-client nrepl hostname port)
                           (assoc ctx :error (e/conflict))

                           :else
                           (let [client (e.p.nrepl/add-client! nrepl hostname port)]
                             (e.p.nrepl/switch-client! nrepl client)
                             (assoc ctx :client client))))
            {:as result :keys [error hostname port]} (e.p.interceptor/execute interceptor
                                                                              e.c.interceptor/connect
                                                                              context connect-fn)]
        (cond
          (e/fault? error)
          (e.message/warning host (format "Host or port is not specified: %s"
                                          {:hostname hostname :port port}))

          (e/conflict? error)
          (e.message/warning host (format "Already connected to %s:%s"
                                          hostname port))

          (contains? result :client)
          (e.message/info host (format "Connected to %s:%s" hostname port)))))))

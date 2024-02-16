(ns elin.handler.connect
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.log :as e.log]
   [elin.protocol.interceptor :as e.p.interceptor]
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
  [{:as elin :component/keys [nrepl interceptor host] :keys [message]}]
  (let [[{:keys [hostname port]} error] (e.u.param/parse ?Params (:params message))]
    (if error
      (e.log/error host "Invalid parameter" error)
      (let [intercept #(apply e.p.interceptor/execute interceptor e.c.interceptor/connect %&)
            result (-> {:elin elin :hostname hostname :port port}
                       (intercept
                        (fn [{:as ctx :keys [hostname port]}]
                          (if (and hostname port)
                            (let [client (e.p.nrepl/add-client! nrepl hostname port)]
                              (e.p.nrepl/switch-client! nrepl client)
                              (assoc ctx :client client))
                            ctx))))]
        (if (contains? result :client)
          (e.log/info host (format "Connected to %s:%s" (:hostname result) (:port result)))
          (e.log/warning host "Host or port is not specified." (pr-str (select-keys result [:hostname :port]))))))))

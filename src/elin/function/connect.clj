(ns elin.function.connect
  (:require
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.map :as e.u.map]
   [malli.core :as m]))

(defn- retry-on-connect-failure
  [f]
  (loop []
    (let [res (try (f)
                   (catch Exception ex
                     (e/fault {:message (ex-message ex)})))]
      (if (e/error? res)
        (do (Thread/sleep 200)
            (recur))
        res))))

(defn connect
  [{:as elin :component/keys [interceptor]}
   {:keys [hostname port language wait?]}]
  (let [context (-> elin
                    (e.u.map/select-keys-by-namespace :component)
                    (assoc :hostname hostname
                           :port port
                           :language language
                           :wait? wait?))
        connect-fn (fn [{:as ctx :component/keys [nrepl]
                         :keys [error hostname port language port-file wait?]}]
                     (cond
                       error
                       ctx

                       (or (not hostname) (not port))
                       (assoc ctx :error (e/fault))

                       (e.p.nrepl/get-client nrepl hostname port)
                       (assoc ctx :error (e/conflict))

                       :else
                       (try
                         (let [add-client! #(e.p.nrepl/add-client! nrepl {:host hostname
                                                                          :port port
                                                                          :language language
                                                                          :port-file port-file})
                               client (if wait?
                                        (retry-on-connect-failure add-client!)
                                        (add-client!))]
                           (e.p.nrepl/switch-client! nrepl client)
                           (assoc ctx :client client))
                         (catch Exception ex
                           (assoc ctx :error (e/fault {:message (ex-message ex)}))))))]
    (e.p.interceptor/execute interceptor
                             e.c.interceptor/connect
                             context connect-fn)))

(defn disconnect
  [{:as elin :component/keys [interceptor]}
   client]
  (let [{:keys [host port]} (:connection client)
        context (-> elin
                    (e.u.map/select-keys-by-namespace :component)
                    (assoc :hostname host
                           :port port))]
    (e.p.interceptor/execute
      interceptor e.c.interceptor/disconnect context
      (fn [{:as ctx :component/keys [nrepl] :keys [hostname port]}]
        (if-let [client' (e.p.nrepl/get-client nrepl hostname port)]
          (if (e.p.nrepl/remove-client! nrepl client')
            (assoc ctx :responce true)
            (assoc ctx :error (e/fault)))
          (assoc ctx :error (e/not-found)))))))

(m/=> client-identifier [:=> [:cat e.s.nrepl/?Client] string?])
(defn client-identifier
  [client]
  (or (:port-file client)
      (str (get-in client [:connection :host])
           ":"
           (get-in client [:connection :port]))))

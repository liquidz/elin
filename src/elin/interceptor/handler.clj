(ns elin.interceptor.handler
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.error :as e]
   [elin.message :as e.message]
   [elin.protocol.host :as e.p.host]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(def handling-error
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                (e.message/error host (ex-message response)))
              (ix/when (comp e/error? :response))
              (ix/discard))})

(def setting-nrepl-connection-status
  "Interceptor for setting nREPL connection status to variable."
  (let [status-handler? #(= :elin.handler.internal/status
                            (get-in % [:message :method]))]
    {:kind e.c.interceptor/handler
     :leave (-> (fn [{:as ctx :component/keys [host] :keys [response]}]
                  (let [config (e.u.interceptor/config ctx #'setting-nrepl-connection-status)
                        {:keys [variable]} config]
                    (when (and (string? variable)
                               (string? response)
                               (seq variable)
                               (seq response))
                      (e.p.host/set-variable! host variable response))))
                (ix/when status-handler?)
                (ix/discard))}))

(def show-result-as-popup
  "Interceptor to show handler result as popup."
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:as ctx :component/keys [host] :keys [response]}]
                (when (and (string? response)
                           (seq response))
                  (let [config (e.u.interceptor/config ctx #'show-result-as-popup)
                        options (merge {:line "near-cursor"
                                        :border []
                                        :filetype "clojure"
                                        :moved "any"}
                                       config)]
                    (async/<!!
                     (e.p.host/open-popup! host response options)))))
              (ix/discard))})

(def append-result-to-info-buffer
  "Interceptor to show handler result temporarily."
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:as ctx :component/keys [host] :keys [response]}]
                (when (and (string? response)
                           (seq response))
                  (let [config (or (e.u.interceptor/config ctx #'append-result-to-info-buffer)
                                   {})]
                    (e.p.host/append-to-info-buffer host response config))))
              ;; (e.p.host/append-to-info-buffer host response {:show-temporarily? true})))
              (ix/discard))})

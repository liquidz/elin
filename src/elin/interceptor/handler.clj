(ns elin.interceptor.handler
  (:require
   [clojure.core.async :as async]
   [clojure.java.io :as io]
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
              (ix/discard))})

(def jump-to-file
  "Interceptor to jump to specified file."
  {:kind e.c.interceptor/handler
   :leave (-> (fn [{:component/keys [host] :keys [response]}]
                ;; response should satisfies elin.schema.handler/?JumpToFile
                (let [{:keys [path lnum col]} (when (map? response)
                                                response)]
                  (cond
                    (or (not path) (not lnum) (not col))
                    nil

                    (.exists (io/file path))
                    (async/<!! (e.p.host/jump! host path lnum col))

                    :else
                    (let [path' (async/<!! (e.p.host/input! host "Open this file?: " path))]
                      (when (and (not (e/fault? path'))
                                 (seq path'))
                        (.mkdirs (.getParentFile (io/file path')))
                        (async/<!! (e.p.host/jump! host path' lnum col)))))))
              (ix/discard))})

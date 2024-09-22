(ns elin.interceptor.quickfix
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.file :as e.u.file]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(defn- location-list?
  [context]
  (= :location (:type context)))

(def auto-toggling-vim-quickfix-window-interceptor
  {:kind e.c.interceptor/quickfix
   :leave (-> (fn [{:as ctx :component/keys [host]}]
                (when (and (satisfies? e.p.rpc/IFunction host)
                           (not= :location (:type ctx)))
                  (async/go
                    ;; FIXME wait for the quickfix list to be set
                    (async/<! (async/timeout 500))
                    (if (seq (:list ctx))
                      (e.p.rpc/notify-function host "elin#internal#execute" ["cwindow"])
                      (e.p.rpc/notify-function host "elin#internal#execute" ["cclose"])))))
              (ix/discard))})

(def auto-toggling-vim-location-window-interceptor
  {:kind e.c.interceptor/quickfix
   :leave (-> (fn [{:as ctx :component/keys [host]}]
                (when (and (satisfies? e.p.rpc/IFunction host)
                           (= :location (:type ctx)))
                  (async/go
                    ;; FIXME wait for the quickfix list to be set
                    (async/<! (async/timeout 500))
                    (if (seq (:list ctx))
                      (e.p.rpc/notify-function host "elin#internal#execute" ["lwindow"])
                      (e.p.rpc/notify-function host "elin#internal#execute" ["lclose"])))))
              (ix/discard))})

(def use-selector-for-location-interceptor
  {:kind e.c.interceptor/quickfix
   :leave (-> (fn [{:as ctx :component/keys [host]}]
                (let [candidates (->> (:list ctx)
                                      (keep (fn [{:keys [filename lnum col]}]
                                              (some-> filename
                                                      (e.u.file/encode-path lnum col)))))]
                  (when (seq candidates)
                    (e.p.host/select-from-candidates
                     host candidates 'elin.handler.navigate/jump))))
              (ix/when location-list?)
              (ix/discard))})

(def location-function-hook-interceptor
  {:kind e.c.interceptor/quickfix
   :params []
   :enter (-> (fn [ctx]
                (let [{:keys [params]} (e.u.interceptor/self ctx)]
                  (assoc ctx ::params params)))
              (ix/when location-list?))
   :leave (-> (fn [{:component/keys [host] ::keys [params]}]
                (when (and (seq params)
                           (satisfies? e.p.rpc/IFunction host))
                  (apply e.p.rpc/notify-function host params)))
              (ix/when location-list?)
              (ix/discard))})

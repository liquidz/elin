(ns elin.interceptor.quickfix
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.file :as e.u.file]
   [exoscale.interceptor :as ix]))

(def auto-toggling-vim-quickfix-window-interceptor
  {:name ::auto-toggling-quickfix-window-interceptor
   :kind e.c.interceptor/quickfix
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
  {:name ::auto-toggling-quickfix-window-interceptor
   :kind e.c.interceptor/quickfix
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
  {:name ::use-selector-for-location-interceptor
   :kind e.c.interceptor/quickfix
   :leave (-> (fn [{:as ctx :component/keys [host]}]
                (when (= :location (:type ctx))
                  (let [candidates (->> (:list ctx)
                                        (keep (fn [{:keys [filename lnum col]}]
                                                (some-> filename
                                                        (e.u.file/encode-path lnum col)))))]
                    (when (seq candidates)
                      (e.p.host/select-from-candidates
                       host candidates 'elin.handler.navigate/jump)))))
              (ix/discard))})

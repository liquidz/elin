(ns elin.interceptor.quickfix
  (:require
   [clojure.core.async :as async]
   [elin.constant.interceptor :as e.c.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.interceptor :as e.u.interceptor]
   [exoscale.interceptor :as ix]))

(defn- location-list?
  [context]
  (= :location (:type context)))

(def auto-toggling-vim-quickfix-window
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

(def auto-toggling-vim-location-window
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

(def location-function-hook
  "Interceptor to call any function on host side when location list is updated.
  Required to config like below:
  ```
  {:interceptor {:config-map {elin.interceptor.quickfix/location-function-hook
                              {:function [\"luaeval\" [\"require('telescope.builtin').loclist()\"]]}}}}
  ```"
  {:kind e.c.interceptor/quickfix
   :leave (-> (fn [{:as ctx :component/keys [host]}]
                (let [{:keys [function]} (e.u.interceptor/config ctx #'location-function-hook)]
                  (when (and (seq function)
                             (satisfies? e.p.rpc/IFunction host))
                    (apply e.p.rpc/notify-function host function))))
              (ix/when location-list?)
              (ix/discard))})

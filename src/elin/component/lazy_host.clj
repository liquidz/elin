(ns elin.component.lazy-host
  (:require
   [clojure.core.async :as async]
   [com.stuartsierra.component :as component]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.protocol.host.rpc :as e.p.h.rpc]
   [elin.protocol.lazy-host :as e.p.lazy-host]
   [elin.protocol.rpc :as e.p.rpc]
   [taoensso.timbre :as timbre]))

(defmacro ^:private execute [{:keys [host protocol method args queue]}]
  `(if-let [host# ~host]
     (if (satisfies? ~protocol host#)
       (apply ~method host# ~args)
       (e/unsupported))
     (async/put! ~queue [~method ~@args])))

(defrecord LazyHost
  [;; PARAMS
   host-store
   host-channel]
  component/Lifecycle
  (start [this]
    (let [ch (async/chan)]
      (async/go-loop []
        (if-let [host @host-store]
          (let [[type-or-fn & args] (async/<! ch)]
            (cond
              (= ::request! type-or-fn)
              (let [[ch & args] args
                    res (async/<! (apply e.p.h.rpc/request! host args))]
                (async/put! ch res))

              (some? type-or-fn)
              (apply type-or-fn this args))

            (when type-or-fn
              (recur)))
          (do
            (async/<! (async/timeout 100))
            (recur))))

      (timbre/info "LazyHost component: Started")
      (assoc this :host-channel ch)))
  (stop [this]
    (reset! host-store nil)
    (async/close! host-channel)
    (timbre/info "LazyHost component: Stopped")
    (dissoc this :host-channel))

  e.p.lazy-host/ILazyHost
  (set-host! [_ host]
    (reset! host-store host))

  e.p.h.rpc/IRpc ; {{{
  (request! [_ content]
    (if-let [host @host-store]
      (e.p.h.rpc/request! host content)
      (let [ch (async/promise-chan)]
        (async/put! host-channel [::request! ch content])
        ch)))
  (notify! [_ content]
    (execute {:host @host-store
              :protocol e.p.h.rpc/IRpc
              :method e.p.h.rpc/notify!
              :args [content]
              :queue host-channel}))
  (response! [_ id error result]
    (execute {:host @host-store
              :protocol e.p.h.rpc/IRpc
              :method e.p.h.rpc/response!
              :args [id error result]
              :queue host-channel}))
  (flush! [_]
    (execute {:host @host-store
              :protocol e.p.h.rpc/IRpc
              :method e.p.h.rpc/flush!
              :args []
              :queue host-channel}))
  ;; }}}

  e.p.host/IEvent ; {{{
  (on-connect [_]
    (execute {:host @host-store
              :protocol e.p.host/IEvent
              :method e.p.host/on-connect
              :args []
              :queue host-channel}))
  ;; }}}

  e.p.host/ISign ; {{{
  (place-sign [_ m]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/place-sign
              :args [m]
              :queue host-channel}))
  (unplace-signs-by [_ m]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/unplace-signs-by
              :args [m]
              :queue host-channel}))
  (list-current-signs! [_]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/list-current-signs!
              :args []
              :queue host-channel}))
  (list-all-signs! [_]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/list-all-signs!
              :args []
              :queue host-channel}))
  (refresh-signs [_]
    (execute {:host @host-store
              :protocol e.p.host/ISign
              :method e.p.host/refresh-signs
              :args []
              :queue host-channel}))
  ;; }}}

  e.p.host/IIo ; {{{
  (echo-text [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IIo
              :method e.p.host/echo-text
              :args [text]
              :queue host-channel}))
  (echo-text [_ text highlight]
    (execute {:host @host-store
              :protocol e.p.host/IIo
              :method e.p.host/echo-text
              :args [text highlight]
              :queue host-channel}))
  (echo-message [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IIo
              :method e.p.host/echo-message
              :args [text]
              :queue host-channel}))
  (echo-message [_ text highlight]
    (execute {:host @host-store
              :protocol e.p.host/IIo
              :method e.p.host/echo-message
              :args [text highlight]
              :queue host-channel}))
  (input! [_ prompt default]
    (execute {:host @host-store
              :protocol e.p.host/IIo
              :method e.p.host/input!
              :args [prompt default]
              :queue host-channel}))
  ;; }}}

  e.p.host/IFile ; {{{
  (get-current-working-directory! [_]
    (execute {:host @host-store
              :protocol e.p.host/IFile
              :method e.p.host/get-current-working-directory!
              :args []
              :queue host-channel}))
  (get-current-file-path! [_]
    (execute {:host @host-store
              :protocol e.p.host/IFile
              :method e.p.host/get-current-file-path!
              :args []
              :queue host-channel}))
  (get-cursor-position! [_]
    (execute {:host @host-store
              :protocol e.p.host/IFile
              :method e.p.host/get-cursor-position!
              :args []
              :queue host-channel}))
  (jump! [_ path lnum col]
    (execute {:host @host-store
              :protocol e.p.host/IFile
              :method e.p.host/jump!
              :args [path lnum col]
              :queue host-channel}))
  (jump! [_ path lnum col jump-command]
    (execute {:host @host-store
              :protocol e.p.host/IFile
              :method e.p.host/jump!
              :args [path lnum col jump-command]
              :queue host-channel}))
  ;; }}}

  e.p.host/IVariable ; {{{
  (get-variable! [_ var-name]
    (execute {:host @host-store
              :protocol e.p.host/IVariable
              :method e.p.host/get-variable!
              :args [var-name]
              :queue host-channel}))
  (set-variable! [_ var-name value]
    (execute {:host @host-store
              :protocol e.p.host/IVariable
              :method e.p.host/set-variable!
              :args [var-name value]
              :queue host-channel}))
  ;; }}}

  e.p.host/ISexpr ; {{{
  (get-top-list-sexpr! [_ lnum col]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-top-list-sexpr!
              :args [lnum col]
              :queue host-channel}))
  (get-top-list-sexpr! [_ path lnum col]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-top-list-sexpr!
              :args [path lnum col]
              :queue host-channel}))
  (get-list-sexpr! [_ lnum col]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-list-sexpr!
              :args [lnum col]
              :queue host-channel}))
  (get-list-sexpr! [_ path lnum col]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-list-sexpr!
              :args [path lnum col]
              :queue host-channel}))
  (get-single-sexpr! [_ lnum col]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-single-sexpr!
              :args [lnum col]
              :queue host-channel}))
  (get-single-sexpr! [_ path lnum col]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-single-sexpr!
              :args [path lnum col]
              :queue host-channel}))
  (get-namespace-sexpr! [_]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-namespace-sexpr!
              :args []
              :queue host-channel}))
  (get-namespace-sexpr! [_ path]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/get-namespace-sexpr!
              :args [path]
              :queue host-channel}))
  (replace-list-sexpr! [_ lnum col new-sexpr]
    (execute {:host @host-store
              :protocol e.p.host/ISexpr
              :method e.p.host/replace-list-sexpr!
              :args [lnum col new-sexpr]
              :queue host-channel}))
  ;; }}}

  e.p.host/IPopup ; {{{
  (open-popup! [_ s]
    (execute {:host @host-store
              :protocol e.p.host/IPopup
              :method e.p.host/open-popup!
              :args [s]
              :queue host-channel}))
  (open-popup! [_ s options]
    (execute {:host @host-store
              :protocol e.p.host/IPopup
              :method e.p.host/open-popup!
              :args [s options]
              :queue host-channel}))
  (move-popup [_ popup-id lnum col]
    (execute {:host @host-store
              :protocol e.p.host/IPopup
              :method e.p.host/move-popup
              :args [popup-id lnum col]
              :queue host-channel}))
  (set-popup-text [_ popup-id s]
    (execute {:host @host-store
              :protocol e.p.host/IPopup
              :method e.p.host/set-popup-text
              :args [popup-id s]
              :queue host-channel}))
  (close-popup [_ popup-id]
    (execute {:host @host-store
              :protocol e.p.host/IPopup
              :method e.p.host/close-popup
              :args [popup-id]
              :queue host-channel}))
  ;; }}}

  e.p.host/IVirtualText ; {{{
  (set-virtual-text [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IVirtualText
              :method e.p.host/set-virtual-text
              :args [text]
              :queue host-channel}))
  (set-virtual-text [_ text options]
    (execute {:host @host-store
              :protocol e.p.host/IVirtualText
              :method e.p.host/set-virtual-text
              :args [text options]
              :queue host-channel}))
  (clear-all-virtual-texts [_]
    (execute {:host @host-store
              :protocol e.p.host/IVirtualText
              :method e.p.host/clear-all-virtual-texts
              :args []
              :queue host-channel}))
  ;; }}}

  e.p.host/IBuffer ; {{{
  (set-to-current-buffer [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IBuffer
              :method e.p.host/set-to-current-buffer
              :args [text]
              :queue host-channel}))
  (append-to-info-buffer [_ text]
    (execute {:host @host-store
              :protocol e.p.host/IBuffer
              :method e.p.host/append-to-info-buffer
              :args [text]
              :queue host-channel}))
  (append-to-info-buffer [_ text options]
    (execute {:host @host-store
              :protocol e.p.host/IBuffer
              :method e.p.host/append-to-info-buffer
              :args [text options]
              :queue host-channel}))
  ;; }}}

  e.p.host/IQuickfix ; {{{
  (set-quickfix-list [_ qf-list]
    (execute {:host @host-store
              :protocol e.p.host/IQuickfix
              :method e.p.host/set-quickfix-list
              :args [qf-list]
              :queue host-channel}))
  (set-location-list [_ window-id qf-list]
    (execute {:host @host-store
              :protocol e.p.host/IQuickfix
              :method e.p.host/set-location-list
              :args [window-id qf-list]
              :queue host-channel}))
  ;; }}}

  e.p.host/ISelector ; {{{
  (select-from-candidates [_ candidates callback-handler-symbol]
    (execute {:host @host-store
              :protocol e.p.host/ISelector
              :method e.p.host/select-from-candidates
              :args [candidates callback-handler-symbol]
              :queue host-channel}))
  (select-from-candidates [_ candidates callback-handler-symbol optional-params]
    (execute {:host @host-store
              :protocol e.p.host/ISelector
              :method e.p.host/select-from-candidates
              :args [candidates callback-handler-symbol optional-params]
              :queue host-channel}))
  ;; }}}

  e.p.host/IMark ; {{{
  (get-mark [_ mark-id]
    (execute {:host @host-store
              :protocol e.p.host/IMark
              :method e.p.host/get-mark
              :args [mark-id]
              :queue host-channel}))
  ;; }}}

  e.p.rpc/IFunction ; {{{
  (call-function [_ method params]
    (if-let [host @host-store]
      (e.p.rpc/call-function host method params)
      (let [ch (async/promise-chan)]
        (async/put! host-channel [::call-function ch method params])
        ch)))
  (notify-function [_ method params]
    (if-let [host @host-store]
      (e.p.rpc/notify-function host method params)
      (async/put! host-channel [::notify-function method params]))))
;; }}}

(defn new-lazy-host
  [_]
  (map->LazyHost {:host-store (atom nil)}))

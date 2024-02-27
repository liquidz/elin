(ns elin.component.server.vim
  "https://vim-jp.org/vimdoc-en/channel.html#channel-use"
  (:require
   [cheshire.core :as json]
   [clojure.core.async :as async]
   [clojure.java.io :as io]
   [elin.log :as e.log]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.util.id :as e.u.id]
   [elin.util.server :as e.u.server]
   [msgpack.clojure-extensions])
  (:import
   java.io.EOFException))

(defrecord VimMessage
  [host message response-manager]
  e.p.rpc/IMessage
  (request? [_]
    (and (sequential? message)
         (int? (first message))
         (not= 0 (first message))
         (not (contains? @response-manager (first message)))))

  (response? [_]
    (and (sequential? message)
         (= 2 (count message))
         (int? (first message))
         (contains? @response-manager (first message))))

  (parse-message [this]
    (cond
      (e.p.rpc/response? this)
      (let [[id result] message]
        {:id id
         :error nil
         :result result})

      (e.p.rpc/request? this)
      (let [[id [method params options]] message]
        {:id id
         :method (keyword method)
         :params params
         :options (e.u.server/unformat options)})

      ;; notify
      :else
      (let [[_ [method params options]] message]
        {:method (keyword method)
         :params params
         :options (e.u.server/unformat options)}))))

(defrecord VimHost
  [output-stream response-manager]
  e.p.rpc/IHost
  (request! [_ [method :as content]]
    (let [id (cond
               (= "call" method) (nth content 3)
               (= "expr" method) (nth content 2))
          maybe-ch (when id (async/promise-chan))]
      (when (and id maybe-ch)
        (swap! response-manager assoc id maybe-ch))
      (json/generate-stream content (io/writer output-stream))
      maybe-ch))

  (notify! [_ content]
    (json/generate-stream content (io/writer output-stream)))

  (response! [_ id error result]
    (when id
      (-> [id [error result]]
          (json/generate-stream  (io/writer output-stream)))))

  (flush! [_]
    (.flush output-stream))

  e.p.rpc/IFunction
  (call-function [this method params]
    (e.p.rpc/request! this ["call" method params (e.u.id/next-id)]))

  (notify-function [this method params]
    (e.p.rpc/notify! this ["call" method params]))

  (echo-text [this text]
    (e.p.rpc/echo-text this text "Normal"))
  (echo-text [this text highlight]
    (e.p.rpc/notify! this ["call" "elin#internal#echo" [text highlight]]))

  (echo-message [this text]
    (e.p.rpc/echo-message this text "Normal"))
  (echo-message [this text highlight]
    (e.p.rpc/notify! this ["call" "elin#internal#echom" [text highlight]])))

(defn start-server
  [{:keys [host server-socket on-accept stop-signal]}]
  (let [response-manager (atom {})]
    ;; Client accepting loop
    (loop []
      (try
        (with-open [client-sock (.accept server-socket)]
          (let [output-stream (.getOutputStream client-sock)
                input-stream (io/reader (.getInputStream client-sock))]
            ;; Client message reading loop
            (loop []
              (let [[raw-msg ch] (async/alts!! [stop-signal
                                                (async/thread
                                                  (try
                                                    (json/parse-stream input-stream)
                                                    (catch Exception ex ex)))])]
                (when (and (not= stop-signal ch)
                           raw-msg
                           (not (instance? Exception raw-msg)))
                  ;; (e.log/debug "Vim server received message:" (pr-str raw-msg))
                  (on-accept {:message (map->VimMessage {:host host
                                                         :message raw-msg
                                                         :response-manager response-manager})
                              :host (map->VimHost {:output-stream output-stream
                                                   :response-manager response-manager})})
                  (when-not (.isClosed client-sock)
                    (recur))))))
          (e.log/debug "Client socket is closed"))
        (catch EOFException _
          nil)
        (catch Exception ex
          (e.log/debug "Client connection is closed" (ex-message ex))))
      (when-not (.isClosed server-socket)
        (recur)))))

(ns elin.nrepl.protocol
  (:refer-clojure :exclude [eval load-file]))

(defprotocol IConnection
  (disconnect [this])
  (disconnected? [this])
  (notify [this msg])
  (request [this msg]))

(defprotocol IClient
  (supported-op? [this op]))

(defprotocol IClientManager
  (add-client! [this host port] [this client])
  (remove-client! [this client])
  (get-client [this host port] [this client-key])
  (switch-client! [this client])
  (current-client [this]))

(defprotocol INreplOp
  (close-op [this])
  (eval-op [this code options])
  (interrupt-op [this options])
  (load-file-op [this file options]))

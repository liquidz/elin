(ns elin.protocol.nrepl)

(defprotocol IConnection
  (disconnect [this])
  (disconnected? [this])
  (notify [this msg])
  (request [this msg]))

(defprotocol IClient
  (supported-op? [this op])
  (current-session [this])
  (version [this]))

(defprotocol IClientManager
  (add-client! [this host port] [this client])
  (remove-client! [this client])
  (remove-all! [this])
  (get-client [this host port] [this client-key])
  (switch-client! [this client])
  (current-client [this])
  (all-clients [this]))

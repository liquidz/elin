(ns elin.protocol.host.rpc)

(defprotocol IRpcMessage
  (request? [this])
  (response? [this])
  (parse-message [this]))

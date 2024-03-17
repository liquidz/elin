(ns elin.protocol.host.rpc)

(defprotocol IRpcMessage
  (request? [this])
  (response? [this])
  (parse-message [this]))

(defprotocol IRpc
  (request! [this content])
  (notify! [this content])
  (response! [this id error result])
  (flush! [this]))

(ns elin.protocol.rpc)

(defprotocol IRpc
  (request? [this])
  (response? [this])
  (parse-request [this])
  (request! [this content])
  (notify! [this content])
  (response! [this error result]))

(defprotocol IHost
  (call-function [this method params])
  (echo-text [this text])
  (echo-message [this text] [this text highlight]))

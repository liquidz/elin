(ns elin.protocol.rpc)

(defprotocol IMessage
  (request? [this])
  (response? [this])
  (parse-message [this])
  (request! [this content])
  (notify! [this content])
  (response! [this error result]))

(defprotocol IFunction
  (call-function [this method params])
  (echo-text [this text])
  (echo-message [this text] [this text highlight]))

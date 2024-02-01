(ns elin.protocol.rpc)

(defprotocol IMessage
  (request? [this])
  (response? [this])
  (parse-message [this]))

(defprotocol ILazyWriter
  (set-writer! [this writer]))

(defprotocol IWriter
  (request! [this content])
  (notify! [this content])
  (response! [this id error result])
  (flush! [this]))

(defprotocol IFunction
  (call-function [this method params])
  (notify-function [this method params])
  (echo-text [this text])
  (echo-message [this text] [this text highlight]))

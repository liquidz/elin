(ns elin.protocol.rpc)

(defprotocol IFunction
  (call-function [this method params])
  (notify-function [this method params])
  (echo-text [this text] [this text highlight])
  (echo-message [this text] [this text highlight]))

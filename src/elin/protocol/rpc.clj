(ns elin.protocol.rpc)

(defprotocol IFunction
  (call-function [this method params])
  (notify-function [this method params]))

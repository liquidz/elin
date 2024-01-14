(ns elin.protocol.rpc)

(defprotocol IRpc
  (request? [this])
  (parse-request [this])
  (request! [this content])
  (notify! [this content])
  ;; (request! [this method params])
  ;; (notify! [this method params])
  (response! [this error result]))

(defprotocol IHost
  (call-function [this method params]))
  ;;(request-function  [this method params id]))

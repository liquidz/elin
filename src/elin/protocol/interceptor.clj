(ns elin.protocol.interceptor)

(defprotocol IInterceptor
  (execute
    [this kind context]
    [this kind context terminator]))

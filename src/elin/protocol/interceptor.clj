(ns elin.protocol.interceptor)

(defprotocol IInterceptor
  (add-interceptors!
    [this interceptor]
    [this kind interceptor])
  (remove-interceptor!
    [this interceptor]
    [this kind interceptor])
  (execute
    [this kind context]
    [this kind context terminator]))

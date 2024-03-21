(ns elin.protocol.lazy-host)

(defprotocol ILazyHost
  (set-host! [this host]))

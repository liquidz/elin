(ns elin.protocol.host)

(defprotocol ILazyHost
  (set-host! [this host]))

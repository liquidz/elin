(ns elin.protocol.host)

(defprotocol ILazyHost
  (set-host! [this host]))

(defprotocol IEcho
  (echo-text [this text] [this text highlight])
  (echo-message [this text] [this text highlight]))

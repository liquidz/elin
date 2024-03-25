(ns elin.protocol.host)

(defprotocol IEcho
  (echo-text [this text] [this text highlight])
  (echo-message [this text] [this text highlight]))

(defprotocol ISign
  (place-sign [this m])
  (unplace-signs-by [this m])
  (list-current-signs!! [this])
  (list-all-signs!! [this])
  (refresh-signs [this]))

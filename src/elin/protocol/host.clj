(ns elin.protocol.host)

(defprotocol IEcho
  (echo-text [this text] [this text highlight])
  (echo-message [this text] [this text highlight]))

(defprotocol IIo
  (input!! [this prompt default]))

(defprotocol IFile
  (get-current-working-directory!! [this])
  (get-current-file-path!! [this])
  (get-cursor-position!! [this])
  (jump!! [this path lnum col] [this path lnum col jump-command]))

(defprotocol ISign
  (place-sign [this m])
  (unplace-signs-by [this m])
  (list-current-signs!! [this])
  (list-all-signs!! [this])
  (refresh-signs [this]))

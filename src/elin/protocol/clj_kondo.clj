(ns elin.protocol.clj-kondo)

(defprotocol ICljKondo
  (analyze [this])
  (restore [this])
  (analyzing? [this])
  (analyzed? [this])
  (analysis [this])
  (analyze-code!! [this code]))

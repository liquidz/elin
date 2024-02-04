(ns elin.protocol.handler)

(defprotocol IHandler
  (add-handlers! [this handler-syms]))

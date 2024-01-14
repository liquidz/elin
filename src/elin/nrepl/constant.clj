(ns elin.nrepl.constant)

(def eval-option-keys
  #{:column
    :eval
    :file
    :line
    :ns
    :read-cond
    :nrepl.middleware.caught/caught
    :nrepl.middleware.caught/print?
    :nrepl.middleware.print/buffer-size
    :nrepl.middleware.print/keys
    :nrepl.middleware.print/options
    :nrepl.middleware.print/print
    :nrepl.middleware.print/quota
    :nrepl.middleware.print/stream?})

(def load-file-option-keys
  #{:file-name
    :file-path
    :nrepl.middleware.caught/caught
    :nrepl.middleware.caught/print?
    :nrepl.middleware.print/buffer-size
    :nrepl.middleware.print/keys
    :nrepl.middleware.print/options
    :nrepl.middleware.print/print
    :nrepl.middleware.print/quota
    :nrepl.middleware.print/stream?})

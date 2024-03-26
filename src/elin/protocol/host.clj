(ns elin.protocol.host
  "Request functions should be suffixed with `!` and return a core.async channel.
  Notify functions should not be suffixed with `!`.")

(defprotocol IEcho
  (echo-text [this text] [this text highlight])
  (echo-message [this text] [this text highlight]))

(defprotocol IIo
  (input! [this prompt default]))

(defprotocol IFile
  (get-current-working-directory! [this])
  (get-current-file-path! [this])
  (get-cursor-position! [this])
  (jump! [this path lnum col] [this path lnum col jump-command]))

(defprotocol IVariable
  (get-variable! [this var-name])
  (set-variable! [this var-name value]))

(defprotocol ISign
  (place-sign [this m])
  (unplace-signs-by [this m])
  (list-current-signs! [this])
  (list-all-signs! [this])
  (refresh-signs [this]))

(defprotocol ISexpr
  (get-top-list-sexpr! [this lnum col])
  (get-list-sexpr! [this lnum col])
  (get-single-sexpr! [this lnum col])
  (get-namespace-form! [this])
  (replace-namespace-form! [this new-ns-form]))

(defprotocol IPopup
  (open-popup! [this s] [this s options])
  (move-popup [this popup-id lnum col])
  (set-popup-text [this popup-id s])
  (close-popup [this popup-id]))

(defprotocol ISelector
  (select-from-candidates
    [this candidates callback-handler-symbol]
    [this candidates callback-handler-symbol optional-params]))

(ns elin.protocol.host
  "Request functions should be suffixed with `!` and return a core.async channel.
  Notify functions should not be suffixed with `!`.")

(defprotocol IEvent
  (on-connect [this]))

(defprotocol IIo
  (echo-text
    [this text]
    [this text highlight])
  (echo-message
    [this text]
    [this text highlight])
  (input!
    [this prompt default]))

(defprotocol IFile
  (get-current-working-directory!
    [this])
  (get-current-file-path!
    [this])
  (get-cursor-position!
    [this])
  (jump!
    [this path lnum col]
    [this path lnum col jump-command]))

(defprotocol IVariable
  (get-variable!
    [this var-name])
  (set-variable!
    [this var-name value]))

(defprotocol ISign
  (place-sign
    [this m])
  (unplace-signs-by
    [this m])
  (list-current-signs!
    [this])
  (list-all-signs!
    [this])
  (refresh-signs
    [this]))

(defprotocol ISexpr
  (get-top-list-sexpr!
    [this lnum col]
    [this path lnum col])
  (get-list-sexpr!
    [this lnum col]
    [this path lnum col])
  (get-single-sexpr!
    [this lnum col]
    [this path lnum col])
  (get-namespace-sexpr!
    [this]
    [this path])
  (replace-list-sexpr!
    [this lnum col new-sexpr]))

(defprotocol IPopup
  (open-popup!
    [this s]
    [this s options])
  (move-popup
    [this popup-id lnum col])
  (set-popup-text
    [this popup-id s])
  (close-popup
    [this popup-id]))

(defprotocol IVirtualText
  (set-virtual-text
    [this text]
    [this text options])
  (clear-all-virtual-texts
    [this]))

(defprotocol IBuffer
  (set-to-current-buffer
    [this text])
  (append-to-info-buffer
    [this text]
    [this text options])
  (get-lines
    [this]
    [this start-lnum]
    [this start-lnum end-lnum]))

(defprotocol ISelector
  (select-from-candidates
    [this candidates callback-handler-symbol]
    [this candidates callback-handler-symbol optional-params]))

(defprotocol IQuickfix
  (set-quickfix-list
    [this quickfix-list])
  (set-location-list
    [this window-id location-list]))

(defprotocol IMark
  (get-mark
    [this mark-id]))

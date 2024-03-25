(ns elin.component.server.impl.file
  (:require
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [elin.schema.host :as e.s.host]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> get-current-working-directory!!* [:=> [:cat e.s.server/?Host [:* any?]] (e.schema/error-or string?)])
(defn- get-current-working-directory!!*
  [host & extra-params]
  (let [params (or extra-params [])]
    (e.c.s.function/request!! host "getcwd" params)))

(m/=> get-current-file-path!!* [:=> [:cat e.s.server/?Host] (e.schema/error-or string?)])
(defn- get-current-file-path!!*
  [host]
  (e.c.s.function/request!! host "expand" ["%:p"]))

(m/=> get-cursor-position!!* [:=> [:cat e.s.server/?Host [:* any?]] (e.schema/error-or e.s.host/?Position)])
(defn get-cursor-position!!*
  [host & extra-params]
  (e/let [params (or extra-params [])
          [bufnum lnum col off curswant] (e.c.s.function/request!! host "getcurpos" params)]
    {:bufname bufnum
     :lnum lnum
     :col col
     :off off
     :curswant curswant}))

(m/=> jump!!* [:=> [:cat e.s.server/?Host string? int? int? [:* any?]] [:maybe e.schema/?Error]])
(defn- jump!!*
  [host path lnum col & [jump-command]]
  (let [jump-command (or jump-command "edit")
        res (e.c.s.function/request!! host "elin#internal#jump" [path lnum col jump-command])]
    (when (e/error? res)
      res)))

(extend-protocol e.p.host/IFile
  elin.component.server.vim.VimHost
  (get-current-working-directory!! [this] (get-current-working-directory!!* this))
  (get-current-file-path!! [this] (get-current-file-path!!* this))
  (get-cursor-position!! [this] (get-cursor-position!!* this))
  (jump!! [this path lnum col] (jump!!* this path lnum col))
  (jump!! [this path lnum col jump-command] (jump!!* this path lnum col jump-command))

  elin.component.server.nvim.NvimHost
  (get-current-working-directory!! [this] (get-current-working-directory!!* this))
  (get-current-file-path!! [this] (get-current-file-path!!* this))
  (get-cursor-position!! [this] (get-cursor-position!!* this))
  (jump!! [this path lnum col] (jump!!* this path lnum col))
  (jump!! [this path lnum col jump-command] (jump!!* this path lnum col jump-command)))

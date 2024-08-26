(ns elin.component.server.impl.file
  (:require
   [clojure.core.async :as async]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [malli.core :as m]))

(m/=> get-current-working-directory!* [:-> e.c.s.function/?IFunction e.schema/?ManyToManyChannel])
(defn- get-current-working-directory!*
  [host]
  (e.c.s.function/request! host "expand" ["%:p:h"]))

(m/=> get-current-file-path!* [:=> [:cat e.c.s.function/?IFunction] (e.schema/error-or string?)])
(defn- get-current-file-path!*
  [host]
  (e.c.s.function/request! host "expand" ["%:p"]))

(m/=> get-cursor-position!* [:=> [:cat e.c.s.function/?IFunction [:* any?]] e.schema/?ManyToManyChannel])
(defn- get-cursor-position!*
  [host & extra-params]
  (async/go
    (e/let [params (or extra-params [])
            [bufnum lnum col off curswant] (async/<! (e.c.s.function/request! host "getcurpos" params))]
      {:bufname bufnum
       :lnum lnum
       :col col
       :off off
       :curswant curswant})))

(m/=> jump!* [:=> [:cat e.c.s.function/?IFunction string? int? int? [:* any?]] e.schema/?ManyToManyChannel])
(defn- jump!*
  [host path lnum col & [jump-command]]
  (async/go
    (let [jump-command (or jump-command "edit")
          res (async/<! (e.c.s.function/request! host "elin#internal#jump" [path lnum col jump-command]))]
      (when (e/error? res)
        res))))

(extend-protocol e.p.host/IFile
  elin.component.server.vim.VimHost
  (get-current-working-directory! [this] (get-current-working-directory!* this))
  (get-current-file-path! [this] (get-current-file-path!* this))
  (get-cursor-position! [this] (get-cursor-position!* this))
  (jump!
    ([this path lnum col] (jump!* this path lnum col))
    ([this path lnum col jump-command] (jump!* this path lnum col jump-command)))

  elin.component.server.nvim.NvimHost
  (get-current-working-directory! [this] (get-current-working-directory!* this))
  (get-current-file-path! [this] (get-current-file-path!* this))
  (get-cursor-position! [this] (get-cursor-position!* this))
  (jump!
    ([this path lnum col] (jump!* this path lnum col))
    ([this path lnum col jump-command] (jump!* this path lnum col jump-command))))

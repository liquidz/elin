(ns elin.component.server.impl.sexpr
  (:require
   [clojure.core.async :as async]
   [elin.component.server.impl.function :as e.c.s.function]
   [elin.component.server.nvim]
   [elin.component.server.vim]
   [elin.error :as e]
   [elin.protocol.host :as e.p.host]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [malli.core :as m]))

(m/=> get-top-list-sexpr!* [:=> [:cat e.s.component/?LazyHost int? int?] e.schema/?ManyToManyChannel])
(defn- get-top-list-sexpr!*
  [host lnum col]
  (async/go
    (e/-> (e.c.s.function/request! host "elin#internal#sexpr#get_top_list" [lnum col])
          (async/<!)
          (update-keys keyword))))

(m/=> get-list-sexpr!* [:=> [:cat e.s.component/?LazyHost int? int?] e.schema/?ManyToManyChannel])
(defn- get-list-sexpr!*
  [host lnum col]
  (async/go
    (e/-> (e.c.s.function/request! host "elin#internal#sexpr#get_list" [lnum col])
          (async/<!)
          (update-keys keyword))))

(m/=> get-single-sexpr!* [:=> [:cat e.s.component/?LazyHost int? int?] e.schema/?ManyToManyChannel])
(defn- get-single-sexpr!*
  [host lnum col]
  (async/go
    (e/-> (e.c.s.function/request! host "elin#internal#sexpr#get_expr" [lnum col])
          (async/<!)
          (update-keys keyword))))

(m/=> get-namespace-sexpr!* [:=> [:cat e.s.component/?LazyHost] e.schema/?ManyToManyChannel])
(defn- get-namespace-sexpr!*
  [host]
  (async/go
    (e/-> (e.c.s.function/request! host "elin#internal#sexpr#clojure#get_ns_sexpr" [])
          (async/<!)
          (update-keys keyword))))

(m/=> replace-list-sexpr!* [:=> [:cat e.s.component/?LazyHost int? int? string?] e.schema/?ManyToManyChannel])
(defn- replace-list-sexpr!*
  [host lnum col new-sexpr]
  (e.c.s.function/request! host "elin#internal#sexpr#replace_list_sexpr" [lnum col new-sexpr]))

(extend-protocol e.p.host/ISexpr
  elin.component.server.vim.VimHost
  (get-top-list-sexpr! [this lnum col] (get-top-list-sexpr!* this lnum col))
  (get-list-sexpr! [this lnum col] (get-list-sexpr!* this lnum col))
  (get-single-sexpr! [this lnum col] (get-single-sexpr!* this lnum col))
  (get-namespace-sexpr! [this] (get-namespace-sexpr!* this))
  (replace-list-sexpr! [this lnum col new-sexpr] (replace-list-sexpr!* this lnum col new-sexpr))

  elin.component.server.nvim.NvimHost
  (get-top-list-sexpr! [this lnum col] (get-top-list-sexpr!* this lnum col))
  (get-list-sexpr! [this lnum col] (get-list-sexpr!* this lnum col))
  (get-single-sexpr! [this lnum col] (get-single-sexpr!* this lnum col))
  (get-namespace-sexpr! [this] (get-namespace-sexpr!* this))
  (replace-list-sexpr! [this lnum col new-sexpr] (replace-list-sexpr!* this lnum col new-sexpr)))

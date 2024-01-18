(ns elin.function.sexp
  (:require
   [elin.constant.host :as e.c.host]
   [elin.function.host :as e.f.host]
   [elin.schema.server :as e.s.server]
   [malli.core :as m]))

(m/=> luaeval [:=> [:cat e.s.server/?Message string? [:sequential any?]] any?])
(defn- luaeval [msg code args]
  (e.f.host/call-function msg "luaeval" [code args]))

(m/=> get-current-top-list [:=> [:cat e.s.server/?Message int? int?] string?])
(defn get-current-top-list
  [msg cursor-row cursor-col]
  (if (= e.c.host/vim (:host msg))
    "TODO"
    (luaeval msg "require('vim-elin.sexp').get_top_list(_A[1], _A[2])"
             [(dec cursor-row)
              (dec cursor-col)])))

(m/=> get-current-list [:=> [:cat e.s.server/?Message int? int?] string?])
(defn get-current-list
  [msg cursor-row cursor-col]
  (if (= e.c.host/vim (:host msg))
    "TODO"
    (luaeval msg "require('vim-elin.sexp').get_list(_A[1], _A[2])"
             [(dec cursor-row)
              (dec cursor-col)])))

(m/=> get-current-form [:=> [:cat e.s.server/?Message int? int?] string?])
(defn get-current-form
  [msg cursor-row cursor-col]
  (if (= e.c.host/vim (:host msg))
    "TODO"
    (luaeval msg "require('vim-elin.sexp').get_form(_A[1], _A[2])"
             [(dec cursor-row)
              (dec cursor-col)])))

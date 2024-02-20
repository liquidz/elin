(ns elin.function.nrepl.system
  (:require
   [clojure.edn :as edn]
   [elin.error :as e]
   [elin.function.nrepl :as e.f.nrepl]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.schema :as e.schema]
   [elin.schema.component :as e.s.component]
   [elin.util.function :as e.u.function]
   [malli.core :as m]))

(def ^:private get-system-info-code
  `(let [user-dir# (System/getProperty "user.dir")
         sep# (System/getProperty "file.separator")]
     {:user-dir user-dir#
      :file-separator sep#
      :project-name (-> (.split user-dir# sep#) seq last)}))

(def ^:private ?SystemInfo
  [:map
   [:user-dir string?]
   [:file-separator string?]
   [:project-name string?]])

(m/=> get-system-info* [:=> [:cat e.s.component/?Nrepl] [:or e.schema/?Error ?SystemInfo]])
(defn- get-system-info*
  [nrepl]
  (e/->> (str get-system-info-code)
         (e.f.nrepl/eval!! nrepl)
         (:value)
         (edn/read-string)))

(def get-system-info
  (e.u.function/memoize-by
   (comp e.p.nrepl/current-session first)
   get-system-info*))

(m/=> get-user-dir [:=> [:cat e.s.component/?Nrepl] [:or e.schema/?Error string?]])
(defn get-user-dir
  [nrepl]
  (e/-> (get-system-info nrepl)
        (:user-dir)))

(m/=> get-file-separator [:=> [:cat e.s.component/?Nrepl] [:or e.schema/?Error string?]])
(defn get-file-separator
  [nrepl]
  (e/-> (get-system-info nrepl)
        (:file-separator)))

(m/=> get-project-name [:=> [:cat e.s.component/?Nrepl] [:or e.schema/?Error string?]])
(defn get-project-name
  [nrepl]
  (e/-> (get-system-info nrepl)
        (:project-name)))

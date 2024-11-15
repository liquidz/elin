(ns elin.handler.lookup
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.error :as e]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.schema.nrepl :as e.s.nrepl]
   [elin.util.handler :as e.u.handler]
   [elin.util.sexpr :as e.u.sexpr]
   [malli.core :as m]
   [pogonos.core :as pogonos]))

(m/=> generate-doc [:=> [:cat map? e.s.nrepl/?RenderingData] string?])
(defn- generate-doc
  [format-config {:as rendering-data :keys [format-type]}]
  (if-let [format-str (get format-config format-type)]
    (pogonos/render-string format-str rendering-data)
    (e/unsupported {:message (str "Not supported format type: " format-type)})))

(defn- parse-code-to-ns-and-name
  [code]
  (let [[head tail] (str/split code #"/" 2)]
    (if tail
      [head tail]
      ["user" head])))

(m/=> lookup [:=> [:cat e.s.handler/?Elin] any?])
(defn lookup
  "Look up symbol at cursor position."
  [{:as elin :component/keys [host]}]
  (e/let [config (e.u.handler/config elin #'lookup)
          {:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          ns-str (e/error-or (e.f.sexpr/get-namespace elin))
          resp (if ns-str
                 (e.f.lookup/lookup elin ns-str code)
                 (->> (parse-code-to-ns-and-name code)
                      (apply e.f.lookup/lookup elin)))]
    (generate-doc (:format config)
                  (e.f.lookup/get-lookup-rendering-data resp))))

(defn show-source
  "Show source code of symbol at cursor position."
  [{:as elin :component/keys [host]}]
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          ns-str (e/error-or (e.f.sexpr/get-namespace elin))
          resp (if ns-str
                 (e.f.lookup/lookup elin ns-str code)
                 (->> (parse-code-to-ns-and-name code)
                      (apply e.f.lookup/lookup elin)))]
    (e.u.sexpr/extract-form-by-position
      (slurp (:file resp))
      (:line resp)
      (:column resp))))

(defn show-clojuredocs
  "Show clojuredocs of symbol at cursor position."
  [elin]
  (e/let [config (e.u.handler/config elin #'show-clojuredocs)
          export-edn-url (:export-edn-url (e.u.handler/config elin #'show-clojuredocs))
          resp (e.f.lookup/clojuredocs-lookup elin export-edn-url)]
    (generate-doc {:clojuredocs (:format config)}
                  (e.f.lookup/get-clojuredocs-rendering-data resp))))

(ns elin.handler.lookup
  (:require
   [clojure.core.async :as async]
   [clojure.string :as str]
   [elin.constant.lookup :as e.c.lookup]
   [elin.error :as e]
   [elin.function.lookup :as e.f.lookup]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.protocol.host :as e.p.host]
   [elin.schema.handler :as e.s.handler]
   [elin.util.handler :as e.u.handler]
   [elin.util.sexpr :as e.u.sexpr]
   [malli.core :as m]))

(defn- generate-javadoc
  [lookup-resp]
  (e/let [title [(format "*%s*" (if-let [member-str (:member lookup-resp)]
                                  (format "%s/%s" (:class lookup-resp) member-str)
                                  (:class lookup-resp)))]
          arglist (when-let [arglists-str (:arglists-str lookup-resp)]
                    (->> (str/split-lines arglists-str)
                         (map #(str "  " %))))
          doc-str (or (:doc lookup-resp) "No document")
          doc-str (if (string? doc-str) doc-str "No document")
          docs (->> (str "  " doc-str)
                    (str/split-lines))
          returns (when-let [returns (:returns lookup-resp)]
                    [""
                     e.c.lookup/subsection-separator
                     "*Returns*"
                     (format "  %s" returns)])
          javadoc (when-let [javadoc (:javadoc lookup-resp)]
                    [""
                     javadoc])]
    (->> (concat title arglist docs returns javadoc)
         (remove nil?)
         (str/join "\n"))))

(defn- generate-cljdoc
  [lookup-resp]
  (e/let [_ (when (not (contains? lookup-resp :name))
              (e/fault))
          title [(format "*%s*" (if-let [ns-str (:ns lookup-resp)]
                                  (format "%s/%s" ns-str (:name lookup-resp))
                                  (:name lookup-resp)))]
          arglist (when-let [arglists-str (:arglists-str lookup-resp)]
                    (->> (str/split-lines arglists-str)
                         (map #(str "  " %))))
          doc-str (or (:doc lookup-resp) "No document")
          doc-str (if (string? doc-str) doc-str "No document")
          docs (->> (str "  " doc-str)
                    (str/split-lines))
          ;; TODO spec
          see-also (when-let [see-also (:see-also lookup-resp)]
                     (concat
                      [""
                       e.c.lookup/subsection-separator
                       "*see-also*"]
                      (map #(format " - %s" %) see-also)))]
    (->> (concat title arglist docs see-also)
         (remove nil?)
         (str/join "\n"))))

(defn- generate-doc
  [lookup-resp]
  (if (contains? lookup-resp :javadoc)
    (generate-javadoc lookup-resp)
    (generate-cljdoc lookup-resp)))

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
  (e/let [{:keys [lnum col]} (async/<!! (e.p.host/get-cursor-position! host))
          {:keys [code]} (e.f.sexpr/get-expr elin lnum col)
          ns-str (e/error-or (e.f.sexpr/get-namespace elin))
          resp (if ns-str
                 (e.f.lookup/lookup elin ns-str code)
                 (->> (parse-code-to-ns-and-name code)
                      (apply e.f.lookup/lookup elin)))]
    (generate-doc resp)))

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

(defn- generate-clojuredocs-content
  [{:as doc :keys [examples see-alsos notes]}]
  (->> [(format "# %s/%s" (:ns doc) (:name doc))
        (->> (:arglists doc)
             (map #(format "(%s %s)" (:name doc) %))
             (str/join " ")
             (format "`%s`"))
        ""
        (format "  %s" (:doc doc))

        ;; Examples
        (when (seq examples)
          [""
           (format "## %d %s" (count examples) (if (= 1 (count examples)) "example" "examples"))
           ""
           (->> examples
                (map-indexed (fn [idx eg]
                               (format "### Example %d\n```\n%s\n```"
                                       (inc idx)
                                       (str/trim eg))))
                (str/join "\n\n"))])

        ;; See also
        (when (seq see-alsos)
          [""
           "## See also"
           ""
           (->> see-alsos
                (map #(format "* %s" %))
                (str/join "\n"))])

        ;; Notes
        (when (seq notes)
          [""
           (format "## %d %s" (count notes) (if (= 1 (count notes)) "note" "notes"))
           ""
           (->> notes
                (map-indexed (fn [idx note]
                               (format "### Note %d\n  %s" (inc idx) (str/trim note))))
                (str/join "\n\n"))])]

       (flatten)
       (remove nil?)
       (str/join "\n")))

(defn show-clojuredocs
  "Show clojuredocs of symbol at cursor position."
  [elin]
  (e/let [export-edn-url (:export-edn-url (e.u.handler/config elin #'show-clojuredocs))
          doc (e.f.lookup/clojuredocs-lookup elin export-edn-url)]
    (generate-clojuredocs-content doc)))

(ns elin.function.nrepl.cider.stacktrace
  (:require
   [clojure.string :as str]
   [elin.util.file :as e.u.file]))

(defn analyzed-last-stacktrace->str
  "Example input:
    {:phase []
      :class \"clojure.lang.ExceptionInfo\",
      :stacktrace [{:fn \"eval135925\", :method \"invokeStatic\", :ns \"foo.core\", :name \"foo.core$eval135925/invokeStatic\",
                    :file \"NO_SOURCE_FILE\", :type \"clj\", :file-url \"\", :line 89, :var \"foo.core/eval135925\",
                    :class \"foo.core$eval135925\", :flags [\"project\" \"repl\" \"clj\"]}
                   .....
                   {:class \"nrepl.SessionThread\", :file \"SessionThread.java\", :file-url [], :flags [\"tooling\" \"java\"],
                    :line 21, :method \"run\", :name \"nrepl.SessionThread/run\", :type \"java\"}],
      :compile-like \"false\",
      :location {},
      :message \"foo\",
      :session \"6c35d632-6361-4be0-967a-7125524abf3b\",
      :data \"{}\"}"
  [{class-str :class stacktrace :stacktrace data :data}
   {:keys [ignoring-vars]}]
  (when (and (seq class-str)
             (seq stacktrace))
    (->> [class-str
          (when (seq data)
            (format "  data: %s" data))
          (for [{name-str :name file :file var-str :var file-url :file-url line :line} stacktrace
                :let [name-str (if (seq var-str)
                                 var-str
                                 name-str)
                      file-url (when (string? file-url)
                                 (e.u.file/normalize-path file-url))
                      file-path (if (seq file-url)
                                  file-url
                                  file)]
                :when (not (contains? ignoring-vars name-str))]
            (format "  at %s (%s:%d)" name-str file-path line))]
         (flatten)
         (remove nil?)
         (distinct)
         (str/join "\n"))))

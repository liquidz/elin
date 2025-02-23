(ns elin.function.nrepl.cider.stacktrace-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.function.nrepl.cider.stacktrace :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(def ^:private test-input
  {:phase []
   :class "clojure.lang.ExceptionInfo"
   :stacktrace [{:fn "eval135925"
                 :method "invokeStatic"
                 :ns "foo.core"
                 :name "foo.core$eval135925/invokeStatic"
                 :file "NO_SOURCE_FILE"
                 :type "clj"
                 :file-url ""
                 :line 89
                 :var "foo.core/eval135925"
                 :class "foo.core$eval135925"
                 :flags ["project" "repl" "clj"]}
                {:class "nrepl.SessionThread"
                 :file "SessionThread.java"
                 :file-url []
                 :flags ["tooling" "java"]
                 :line 21
                 :method "run"
                 :name "nrepl.SessionThread/run"
                 :type "java"}]
   :compile-like "false"
   :location {}
   :message "foo"
   :session "6c35d632-6361-4be0-967a-7125524abf3b"
   :data "{}"})

(t/deftest analyzed-last-stacktrace->str-test
  (t/is (= (->> ["clojure.lang.ExceptionInfo"
                 "  data: {}"
                 "  at foo.core/eval135925 (NO_SOURCE_FILE:89)"
                 "  at nrepl.SessionThread/run (SessionThread.java:21)"]
                (str/join "\n"))
          (sut/analyzed-last-stacktrace->str test-input {})))

  (t/testing "ignoring-vars"
    (t/is (= (->> ["clojure.lang.ExceptionInfo"
                   "  data: {}"
                   "  at foo.core/eval135925 (NO_SOURCE_FILE:89)"]
                  (str/join "\n"))
            (sut/analyzed-last-stacktrace->str
              test-input
              {:ignoring-vars #{"nrepl.SessionThread/run"}})))))

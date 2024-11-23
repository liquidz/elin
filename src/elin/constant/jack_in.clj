(ns elin.constant.jack-in)

(def clojure-cli :clojure-cli)
(def leiningen :leiningen)
(def babashka :babashka)
(def squint :squint)

(def supported-project-types
  [clojure-cli
   babashka
   squint])

(def clojure-command
  (or (System/getenv "ELIN_REPL_CLOJURE_CLI_CMD")
      "clj"))

(def babashka-command
  (or (System/getenv "ELIN_REPL_BABASHKA_CMD")
      "bb"))

(def squint-command
  (or (System/getenv "ELIN_REPL_SQUINT_CMD")
      "squint"))

(def deno-command
  (or (System/getenv "ELIN_REPL_DENO_CMD")
      "deno"))

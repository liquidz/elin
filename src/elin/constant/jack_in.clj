(ns elin.constant.jack-in)

(def clojure-cli :clojure-cli)
(def leiningen :leiningen)
(def babashka :babashka)
(def squint :squint)
(def nbb :nbb)

(def supported-project-types
  [clojure-cli
   leiningen
   babashka
   squint
   nbb])

(def clojure-command
  (or (System/getenv "ELIN_REPL_CLOJURE_CLI_CMD")
      "clj"))

(def leiningen-command
  (or (System/getenv "ELIN_REPL_LEININGEN_CLI_CMD")
      "lein"))

(def babashka-command
  (or (System/getenv "ELIN_REPL_BABASHKA_CMD")
      "bb"))

(def squint-command
  (or (System/getenv "ELIN_REPL_SQUINT_CMD")
      "squint"))

(def nbb-command
  (or (System/getenv "ELIN_REPL_NBB_CMD")
      "nbb"))

(def deno-command
  (or (System/getenv "ELIN_REPL_DENO_CMD")
      "deno"))

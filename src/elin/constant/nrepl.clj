(ns elin.constant.nrepl)

(def array-key-set
  #{"status" "sessions" "classpath"
    ;; cider-nrepl complete op
    "completions"})

;; nrepl built-in ops

(def close-op "close")
(def completions-op "completions")
(def eval-op "eval")
(def interrupt-op "interrupt")
(def load-file-op "load-file")
(def lookup-op "lookup")
(def ls-sessions-op "ls-sessions")

;; cider-nrepl ops

(def complete-op "complete")
(def info-op "info")
(def ns-path-op "ns-path")
(def reload-all-op "cider.clj-reload/reload-all")
(def reload-op "cider.clj-reload/reload")
(def test-var-query-op "test-var-query")

;; refactor-nrepl ops

(def resolve-missing-op "resolve-missing")

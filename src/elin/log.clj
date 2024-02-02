(ns elin.log
  (:require
   [clojure.string :as str]
   [elin.protocol.rpc :as e.p.rpc]))

(def ^:const DEBUG_LEVEL 3)
(def ^:const INFO_LEVEL 2)
(def ^:const WARNING_LEVEL 1)
(def ^:const ERROR_LEVEL 0)

(def log-level INFO_LEVEL)
(def ^:dynamic *log-file* "/tmp/elin.log")

(defn set-level!
  [level]
  (let [level (cond
                (int? level)
                level

                (keyword? level)
                (case level
                  :debug DEBUG_LEVEL
                  :info INFO_LEVEL
                  :warning WARNING_LEVEL
                  :error ERROR_LEVEL
                  INFO_LEVEL)

                :else
                INFO_LEVEL)]
    (alter-var-root #'log-level (constantly level))))

(defn log
  [& messages]
  (let [s (->> (map str messages)
               (str/join " "))]
    (println s)
    (spit *log-file* (str s "\n") :append true)))

(defn- log*
  [texts highlight]
  (let [[msg & texts] (if (satisfies? e.p.rpc/IFunction (first texts))
                        texts
                        (cons nil texts))
        s (->> (map str texts)
               (str/join " "))]
    (if msg
      (e.p.rpc/echo-message msg s highlight)
      (do
        (println s)
        (spit *log-file* (str s "\n") :append true))))
  nil)

(defn debug
  [& texts]
  (when (<= DEBUG_LEVEL log-level)
    (log* texts "Normal")))

(defn info
  [& texts]
  (when (<= INFO_LEVEL log-level)
    (log* texts "MoreMsg")))

(defn warning
  [& texts]
  (when (<= WARNING_LEVEL log-level)
    (log* texts "WarningMsg")))

(defn error
  [& texts]
  (when (<= ERROR_LEVEL log-level)
    (log* texts "ErrorMsg")))

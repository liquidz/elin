(ns elin.message
  (:require
   [clojure.string :as str]
   [elin.protocol.rpc :as e.p.rpc]))

(defn- log*
  [host texts highlight]
  (let [s (->> (map str texts)
               (str/join " "))]
    (try
      (e.p.rpc/echo-message host s highlight)
      (catch Exception _ nil)))
  nil)

(defn info
  [host & texts]
  (log* host texts "MoreMsg"))

(defn warning
  [host & texts]
  (log* host texts "WarningMsg"))

(defn error
  [host & texts]
  (log* host texts "ErrorMsg"))

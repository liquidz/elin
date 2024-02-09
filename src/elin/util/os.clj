(ns elin.util.os
  (:require
   [clojure.string :as str]))

(def ^:private os-name
  (str/lower-case
   (System/getProperty "os.name")))

(def mac?
  (str/includes? os-name "mac"))

(def windows?
  (str/includes? os-name "win"))

(def linux?
  (str/includes? os-name "linux"))

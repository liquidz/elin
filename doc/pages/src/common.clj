(ns common
  (:require
   [clojure.java.io :as io])
  (:import
   (org.asciidoctor
    Asciidoctor$Factory
    Options
    SafeMode)))

(def ^:private asciidoctor
  (Asciidoctor$Factory/create))

(def ^:private input-file
  (io/file "index.adoc"))

(def ^:private output-file
  (io/file "../../target/html/index.html"))

(defn convert-file
  []
  (.mkdirs (.getParentFile output-file))
  (println "Converting ...")
  (let [options (-> (Options/builder)
                    (.toFile output-file)
                    (.safe SafeMode/UNSAFE)
                    (.build))]
    (.convertFile asciidoctor input-file options)
    (println "Converted")))

(ns elin.util.http
  (:require
   [cheshire.core :as json]))

(defn ok
  [resp]
  {:body resp})

(defn bad-request
  [& [m]]
  (merge {:status 400 :body "Bad request"}
         m))

(defn not-found
  [& [m]]
  (merge {:status 404 :body "Not found"}
         m))

(defn json
  [value]
  (-> value
      (json/generate-string)
      (ok)))

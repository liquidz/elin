(ns elin.util.file-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.file :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest find-file-in-parent-directories-test
  (t/testing "README"
    (t/is (= "= vim-elin"
             (-> (sut/find-file-in-parent-directories "README.adoc")
                 (slurp)
                 (str/split-lines)
                 (first))))
    (t/is (= "= vim-elin"
             (-> (sut/find-file-in-parent-directories "./src" "README.adoc")
                 (slurp)
                 (str/split-lines)
                 (first)))))

  (t/testing "Not found"
    (t/is (nil? (sut/find-file-in-parent-directories (str "non-existing" (random-uuid)))))))

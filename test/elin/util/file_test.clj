(ns elin.util.file-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.file :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest find-file-in-parent-directories-test
  (t/testing "string"
    (t/testing "README"
      (t/is (-> (sut/find-file-in-parent-directories "." "README.adoc")
                (slurp)
                (str/split-lines)
                (first)
                (str/includes? "elin")))
      (t/is (-> (sut/find-file-in-parent-directories "./src" "README.adoc")
                (slurp)
                (str/split-lines)
                (first)
                (str/includes? "elin"))))

    (t/testing "Not found"
      (t/is (nil? (sut/find-file-in-parent-directories "." (str "non-existing" (random-uuid)))))))

  (t/testing "pattern"
    (t/is (= "README.adoc"
             (-> (sut/find-file-in-parent-directories "." #"^README")
                 (.getName))))
    (t/is (nil? (sut/find-file-in-parent-directories "." (re-pattern (str (random-uuid))))))))

(t/deftest normalize-path-test
  (t/is (= "/foo/bar.txt"
           (sut/normalize-path "/foo/bar.txt")))
  (t/is (= "/foo/bar.txt"
           (sut/normalize-path "file:/foo/bar.txt")))
  (t/is (= "zipfile:///path/to/jarfile.jar::path/to/file.clj"
           (sut/normalize-path "jar:file:/path/to/jarfile.jar!/path/to/file.clj")))
  (t/is (nil? (sut/normalize-path nil))))

(t/deftest encode-path-test
  (t/is (= "foo" (sut/encode-path "foo")))
  (t/is (= "foo:1" (sut/encode-path "foo" 1)))
  (t/is (= "foo:1:2" (sut/encode-path "foo" 1 2))))

(t/deftest decode-path-test
  (t/is (= {:path "foo" :lnum 1 :col 1}
           (sut/decode-path "foo")))
  (t/is (= {:path "foo" :lnum 2 :col 1}
           (sut/decode-path "foo:2")))
  (t/is (= {:path "foo" :lnum 2 :col 4}
           (sut/decode-path "foo:2:4")))
  (t/is (= {:path "foo::" :lnum 1 :col 1}
           (sut/decode-path "foo::")))
  (t/is (= {:path "foo:" :lnum 2 :col 1}
           (sut/decode-path "foo::2"))))

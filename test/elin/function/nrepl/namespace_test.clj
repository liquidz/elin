(ns elin.function.nrepl.namespace-test
  (:require
   [clojure.java.io :as io]
   [clojure.test :as t]
   [elin.function.nrepl.namespace :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest get-cycled-namespace-path-test
  (t/testing "src"
    (t/is (= "/path/to/test/foo/bar/baz_test.clj"
             (sut/get-cycled-namespace-path
              {:ns "foo.bar.baz"
               :path "/path/to/src/foo/bar/baz.clj"
               :file-separator "/"}))))

  (t/testing "test"
    (t/is (= "/path/to/src/foo/bar/baz.clj"
             (sut/get-cycled-namespace-path
              {:ns "foo.bar.baz-test"
               :path "/path/to/test/foo/bar/baz_test.clj"
               :file-separator "/"}))))

  (t/testing "another dir"
    (t/is (= "/path/to/foo/bar/baz_test.clj"
             (sut/get-cycled-namespace-path
              {:ns "foo.bar.baz"
               :path "/path/to/foo/bar/baz.clj"
               :file-separator "/"})))

    (t/is (= "/path/to/foo/bar/baz.clj"
             (sut/get-cycled-namespace-path
              {:ns "foo.bar.baz-test"
               :path "/path/to/foo/bar/baz_test.clj"
               :file-separator "/"})))))

(t/deftest guess-namespace-from-path-test
  (t/testing "src"
    (let [src-elin-dir (.getParentFile (io/file "src" "elin" "core.clj"))]
      (t/is (= "elin.aaa"
               (-> (io/file src-elin-dir "aaa.clj")
                   (.getAbsolutePath)
                   (sut/guess-namespace-from-path))))))

  (t/testing "test"
    (let [test-elin-dir (.getParentFile (io/file "test" "elin" "config_test.clj"))]
      (t/is (= "elin.aaa-test"
               (-> (io/file test-elin-dir "aaa_test.clj")
                   (.getAbsolutePath)
                   (sut/guess-namespace-from-path)))))))

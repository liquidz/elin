(ns elin.function.nrepl.namespace-test
  (:require
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

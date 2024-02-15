(ns elin.util.server-test
  (:require
   [clojure.test :as t]
   [elin.test-helper :as h]
   [elin.util.server :as sut]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest format-test
  (t/is (= "foo" (sut/format :foo)))
  (t/is (= "foo/bar" (sut/format :foo/bar)))
  (t/is (= "foo" (sut/format 'foo)))
  (t/is (= "foo/bar" (sut/format 'foo/bar)))
  (t/is (= ["foo" "bar" "baz"]
           (sut/format ["foo" :bar 'baz])))
  (t/is (= {"foo" ["bar" {"baz" true}]}
           (sut/format {:foo ['bar {:baz true}]}))))

(t/deftest unformat-test
  (t/is (= "foo" (sut/unformat "foo")))
  (t/is (= {:foo "bar"} (sut/unformat {"foo" "bar"})))
  (t/is (= [{:foo "bar"}] (sut/unformat [{"foo" "bar"}]))))

(ns elin.util.interceptor-test
  (:require
   [clojure.test :as t]
   [elin.util.interceptor :as sut]))

(t/deftest parse-test
  (t/is (= {:symbol 'foo/bar
            :params []}
           (sut/parse 'foo/bar)))

  (t/is (= {:symbol 'foo/bar
            :params []}
           (sut/parse ['foo/bar])))

  (t/is (= {:symbol 'foo/bar
            :params ["a" "b"]}
           (sut/parse ['foo/bar "a" "b"])))

  (t/is (nil? (sut/parse nil)))
  (t/is (nil? (sut/parse 'foo)))
  (t/is (nil? (sut/parse 'foo))))

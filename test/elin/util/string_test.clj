(ns elin.util.string-test
  (:require
   [clojure.test :as t]
   [elin.util.string :as sut]))

(t/deftest starts-with-upper?-test
  (t/is (false? (sut/starts-with-upper? "foo")))
  (t/is (true? (sut/starts-with-upper? "Foo"))))

(t/deftest java-class-name?-test
  (t/is (false? (sut/java-class-name? "foo")))
  (t/is (false? (sut/java-class-name? "foo.bar")))
  (t/is (false? (sut/java-class-name? "Foo.bar")))
  (t/is (true? (sut/java-class-name? "foo.Bar")))
  (t/is (true? (sut/java-class-name? "Foo"))))

(t/deftest render-test
  (t/is (= "ab"
           (sut/render "a{{a}}" {:a "b"})))
  (t/is (= "ab"
           (sut/render "a{{a/b}}" {:a/b "b"})))
  (t/is (= "a{{c}}"
           (sut/render "a{{c}}" {:a/b "b"})))
  (t/is (= "a1"
           (sut/render "a{{a}}" {:a 1})))
  (t/is (= "a"
           (sut/render "a{{a}}" {:a nil}))))

(t/deftest trim-indent-test
  (t/is (= "foo"
           (sut/trim-indent 0 "foo")))
  (t/is (= "foo\nbar"
           (sut/trim-indent 1 " foo\n bar")))
  (t/is (= " foo\nbar"
           (sut/trim-indent 1 " foo\n bar" 1))))

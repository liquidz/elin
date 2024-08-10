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

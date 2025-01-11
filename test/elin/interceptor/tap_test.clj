(ns elin.interceptor.tap-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.interceptor.tap :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(defmacro define-convert-to-edn-compliant-data-fn [max-depth]
  (#'sut/convert-to-edn-compliant-data-fn-code max-depth))

(t/deftest convert-to-edn-compliant-data-test
  ;; https://github.com/edn-format/edn?tab=readme-ov-file#built-in-elements
  (let [convert (define-convert-to-edn-compliant-data-fn 5)]
    (t/testing "nil"
      (t/is (= nil (convert nil))))

    (t/testing "boolean"
      (t/is (= true (convert true)))
      (t/is (= false (convert false))))

    (t/testing "strings"
      (t/is (= "foo" (convert "foo"))))

    (t/testing "characters"
      (t/is (= \c (convert \c)))
      (t/is (= \newline (convert \newline))))

    (t/testing "symbols"
      (t/is (= 'symbol (convert 'symbol)))
      (t/is (= 'ns/symbol (convert 'ns/symbol))))

    (t/testing "keywords"
      (t/is (= :keyword (convert :keyword)))
      (t/is (= :ns/keyword (convert :ns/keyword))))

    (t/testing "integers"
      (t/is (= 10 (convert 10)))
      (t/is (= -10 (convert -10))))

    (t/testing "floating point numbers"
      (t/is (= 1.2 (convert 1.2)))
      (t/is (= -1.2 (convert -1.2))))

    (t/testing "lists"
      (t/is (= '(1 "foo") (convert '(1 "foo"))))
      (t/testing "lazy sequences"
        (t/is (= '(1 2 3) (convert (map inc (range 3)))))))

    (t/testing "vectors"
      (t/is (= [1 "foo"] (convert [1 "foo"]))))

    (t/testing "maps"
      (t/is (= {:a 1 :b "foo"} (convert {:a 1 :b "foo"}))))

    (t/testing "sets"
      (t/is (= #{1 "foo"} (convert #{1 "foo"}))))

    (t/testing "built-in tagged elements"
      (t/testing "#inst"
        (let [v #inst "1985-04-12T23:20:50.52Z"]
          (t/is (= v (convert v)))))

      (t/testing "#uuid"
        (let [v #uuid "f81d4fae-7dec-11d0-a765-00a0c91e6bf6"]
          (t/is (= v (convert v))))))

    (t/testing "object"
      (t/is (str/starts-with? (convert inc)
                              "clojure.core$inc@")
            "Should be stringified"))

    (t/testing "max-depth"
      (t/testing "lists"
        (t/is (= '(1 (2 (3 (4 (5 (...))))))
                 (convert '(1 (2 (3 (4 (5 (6))))))))))
      (t/testing "vectors"
        (t/is (= '[1 [2 [3 [4 [5 [...]]]]]]
                 (convert [1 [2 [3 [4 [5 [6]]]]]]))))
      (t/testing "maps"
        (t/is (= '{:1 {:2 {:3 {:4 {:5 {... ...}}}}}}
                 (convert {:1 {:2 {:3 {:4 {:5 {:6 :7}}}}}}))))
      (t/testing "sets"
        (t/is (= #{1 #{2 #{3 #{4 #{5 #{'...}}}}}}
                 (convert #{1 #{2 #{3 #{4 #{5 #{6}}}}}})))))

    (t/testing "datafiable"
      (t/testing "Exception"
        (t/is (contains? (convert (Exception. "foo")) :via))))))

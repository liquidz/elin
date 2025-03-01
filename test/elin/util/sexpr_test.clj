(ns elin.util.sexpr-test
  (:require
   [clojure.test :as t]
   [elin.error :as e]
   [elin.util.sexpr :as sut]
   [rewrite-clj.zip :as r.zip]))

(t/deftest extract-ns-form-test
  (t/is (= "(ns bar)"
           (sut/extract-ns-form "(foo) (ns bar) (baz)")))
  (t/is (e/not-found? (sut/extract-ns-form "(foo) (baz)")))
  (t/is (e/not-found? (sut/extract-ns-form ""))))

(t/deftest extract-namespace-test
  (t/testing "ns"
    (t/is (= "foo.bar"
             (sut/extract-namespace "(ns foo.bar)"))))

  (t/testing "in-ns"
    (t/is (= "foo.bar"
             (sut/extract-namespace "(in-ns 'foo.bar)"))))

  (t/is (e/not-found? (sut/extract-namespace "")))
  (t/is (e/not-found? (sut/extract-namespace "(foo bar)"))))

(t/deftest add-require-test
  (t/testing "require exists"
    (t/testing "with alias"
      (t/is (= "(ns foo.core\n  (:require\n   [baz.core :as baz]\n   [bar.core :as bar]))"
               (sut/add-require "(ns foo.core\n  (:require\n   [bar.core :as bar]))"
                                'baz.core
                                'baz))))

    (t/testing "without alias"
      (t/is (= "(ns foo.core\n  (:require\n   baz.core\n   [bar.core :as bar]))"
               (sut/add-require "(ns foo.core\n  (:require\n   [bar.core :as bar]))"
                                'baz.core
                                nil))))

    (t/testing "no linebreak after :require"
      (t/is (= "(ns foo.core\n  (:require [baz.core :as baz]\n            [bar.core :as bar]))"
               (sut/add-require "(ns foo.core\n  (:require [bar.core :as bar]))"
                                'baz.core
                                'baz)))))

  (t/testing "no require"
    (t/is (= "(ns foo.core\n  (:require [baz.core :as baz]))"
             (sut/add-require "(ns foo.core)"
                              'baz.core
                              'baz))))

  (t/testing "no namespace form"
    (t/is (e/not-found? (sut/add-require ""
                                         'baz.core
                                         'baz)))))

(t/deftest add-import-test
  (t/testing "import exists"
    (t/testing "has linebreak after :import"
      (t/is (= "(ns foo.core\n  (:import\n   java.lang.Number\n   java.lang.String))"
               (sut/add-import "(ns foo.core\n  (:import\n   java.lang.String))"
                               'java.lang.Number))))

    (t/testing "no linebreak after :import"
      (t/is (= "(ns foo.core\n  (:import java.lang.Number\n            java.lang.String))"
               (sut/add-import "(ns foo.core\n  (:import java.lang.String))"
                               'java.lang.Number)))))

  (t/testing "no import"
    (t/is (= "(ns foo.core\n  (:import java.lang.String))"
             (sut/add-import "(ns foo.core)"
                             'java.lang.String))))

  (t/testing "no namespace form"
    (t/is (e/not-found? (sut/add-import "" 'java.lang.String)))))

(t/deftest extract-form-by-position-test
  (let [code "(defn- foo [a b] (+ a b))"]
    (t/is (= code
             (sut/extract-form-by-position code 1 1)))
    (t/is (= "defn-"
             (sut/extract-form-by-position code 1 2)))
    (t/is (= "foo"
             (sut/extract-form-by-position code 1 8)))
    (t/is (= "[a b]"
             (sut/extract-form-by-position code 1 12)))
    (t/is (= "(+ a b)"
             (sut/extract-form-by-position code 1 18)))))

(t/deftest apply-cider-coordination-test
  (let [code "(defn- foo [a b] #dbg (+ b (+ a 1)))"]
    (t/is (= {:code "b" :position [0 25]}
             (sut/apply-cider-coordination code [3 1])))

    (t/is (= {:code "a" :position [0 30]}
             (sut/apply-cider-coordination code [3 2 1])))

    (t/is (= {:code "(+ a 1)" :position [0 27]}
             (sut/apply-cider-coordination code [3 2]))))

  (t/testing "Conditional Breakpoints"
    (let [code "(dotimes [i 10] #dbg ^{:break/when (= i 7)} (prn i))"]
      (t/is (= {:code "i" :position [0 49]}
               (sut/apply-cider-coordination code [2 1]))))))

(t/deftest convert-code-to-testing-focused-code-test
  (let [code (str '(do (testing "foo"
                         (foo))
                       (testing "bar"
                         (bar)
                         (testing "baz"
                           (baz)))))
        zloc (r.zip/of-string code {:track-position? true})
        foo-pos (-> zloc
                    (r.zip/find-next-value r.zip/next 'foo)
                    (r.zip/position))
        bar-pos (-> zloc
                    (r.zip/find-next-value r.zip/next 'bar)
                    (r.zip/position))
        baz-pos (-> zloc
                    (r.zip/find-next-value r.zip/next 'baz)
                    (r.zip/position))]
    (t/is (= "(foo)"
             (sut/convert-code-to-testing-focused-code "(foo)" 1 1)))

    (t/is (= (str '(do (testing "foo"
                         (foo))
                       (comment (testing "bar"
                                  (bar)
                                  (comment (testing "baz"
                                             (baz)))))))
             (apply sut/convert-code-to-testing-focused-code code foo-pos)))

    (t/is (= (str '(do (comment (testing "foo"
                                  (foo)))
                       (testing "bar"
                         (bar)
                         (comment (testing "baz"
                                    (baz))))))
             (apply sut/convert-code-to-testing-focused-code code bar-pos)))

    (t/is (= (str '(do (comment (testing "foo"
                                  (foo)))
                       (testing "bar"
                         (bar)
                         (testing "baz"
                           (baz)))))
             (apply sut/convert-code-to-testing-focused-code code baz-pos)))))

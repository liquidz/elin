(ns elin.function.nrepl.cider.test-test
  (:require
   [clojure.test :as t]
   [elin.function.nrepl.cider.test :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(def dummy-success-resp
  {:ns-elapsed-time {:antq.util.file-test {:humanized "Completed in 2 ms" :ms 2}}
   :summary {:error 0 :fail 0 :ns 1 :pass 4 :test 4 :var 1}
   :testing-ns "antq.util.file-test"
   :gen-input []
   :status ["done"]
   :var-elapsed-time {:antq.util.file-test {:normalize-path-test {:elapsed-time {:humanized "Completed in 2 ms" :ms 2}}}}
   :id 17
   :elapsed-time {:humanized "Completed in 2 ms" :ms 2}
   :session "1fe73292-b1ff-41f7-a837-feb71cb0816d"
   :results {:antq.util.file-test {:normalize-path-test [{:context "HOME" :index 0 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}
                                                         {:context "HOME" :index 1 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}
                                                         {:context "Redundant path" :index 2 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}
                                                         {:context "HOME and Redundant path" :index 3 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}]}}})

(def dummy-error-resp
  {:ns-elapsed-time {:antq.util.file-test {:humanized "Completed in 4 ms" :ms 4}}
   :summary {:error 0 :fail 2 :ns 1 :pass 3 :test 5 :var 1}
   :testing-ns "antq.util.file-test"
   :gen-input []
   :status ["done"]
   :var-elapsed-time {:antq.util.file-test {:normalize-path-test {:elapsed-time {:humanized "Completed in 4 ms" :ms 4}}}}
   :id 34
   :elapsed-time {:humanized "Completed in 4 ms" :ms 4}
   :session "1fe73292-b1ff-41f7-a837-feb71cb0816d"
   :results {:antq.util.file-test {:normalize-path-test [{:index 0 :ns "antq.util.file-test" :file "NO_SOURCE_FILE" :type "fail" :line 11 :var "normalize-path-test"
                                                          :expected "\"//path/to/bar\"\n" :context "HOME" :actual "\"/path/to/bar\"\n" :message ""}
                                                         {:context "HOME" :index 1 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}
                                                         {:index 2 :ns "antq.util.file-test" :file "NO_SOURCE_FILE" :type "fail" :diffs [["{:a 2}\n" ["{:a 1}\n" "{:a 2}\n"]]]
                                                          :line 15 :var "normalize-path-test" :expected "{:a 1}\n" :context "Redundant path" :actual "{:a 2}\n" :message ""}
                                                         {:context "Redundant path" :index 3 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}
                                                         {:context "HOME and Redundant path" :index 4 :message "" :ns "antq.util.file-test" :type "pass" :var "normalize-path-test"}]}}})

(def dummy-error-resp-without-expected-values
  {:elapsed-time {:humanized "Completed in 123 ms" :ms 123}
   :gen-input []
   :id 142
   :ns-elapsed-time {:foo.bar-test {:humanized "Completed in 122 ms" :ms 122}}
   :results {:foo.bar-test {:baz-test [{:index 0 :ns "foo.bar-test" :file "AFn.java" :type "error" :fault "true" :line 69 :var "baz-test" :context []
                                        :var-elapsed-time {:humanized "Completed in 1 ms" :ms 1}
                                        :error "clojure.lang.ArityException: Wrong number of args (3) passed to: foo.bar-test/fn--128207/fn--128210"
                                        :message "Uncaught exception not in assertion"}]}}
   :session "9115daa1-91ca-4ebb-ab4c-b9184910b357"
   :summary {:error 1 :fail 0 :ns 1 :pass 0 :test 1 :var 1}
   :testing-ns "foo.bar-test"})

(t/deftest collect-results-test
  (t/is (= [{:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}
            {:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}
            {:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}
            {:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}]
           (sut/collect-results (h/test-nrepl {}) dummy-success-resp)))

  (t/is (= [{:filename "NO_SOURCE_FILE" :text "normalize-path-test: HOME" :expected "\"//path/to/bar\"" :ns "antq.util.file-test" :var "normalize-path-test" :lnum 11 :actual "\"/path/to/bar\"" :result :failed}
            {:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}
            {:ns "antq.util.file-test" :diffs "- {:a 1}\n+ {:a 2}" :lnum 15 :filename "NO_SOURCE_FILE" :var "normalize-path-test" :result :failed :expected "{:a 1}" :actual "{:a 2}" :text "normalize-path-test: Redundant path"}
            {:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}
            {:result :passed :ns "antq.util.file-test" :var "normalize-path-test"}]
           (sut/collect-results (h/test-nrepl {}) dummy-error-resp)))

  (t/is (= [{:filename "AFn.java" :text "baz-test: Uncaught exception not in assertion" :expected "" :ns "foo.bar-test" :var "baz-test" :lnum 69 :result :failed
             :actual "clojure.lang.ArityException: Wrong number of args (3) passed to: foo.bar-test/fn--128207/fn--128210"}]
           (sut/collect-results (h/test-nrepl {}) dummy-error-resp-without-expected-values))))

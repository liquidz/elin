(ns elin.interceptor.autocmd-test
  (:require
   [clojure.test :as t]
   [elin.function.sexpr :as e.f.sexpr]
   [elin.handler.evaluate :as e.h.evaluate]
   [elin.interceptor.autocmd :as sut]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]
   [elin.util.interceptor :as e.u.interceptor]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(def ^:private ns-load-enter
  (:enter sut/ns-load))

(t/deftest ns-load-test
  (let [ctx (assoc (h/test-elin)
                   :autocmd-type "BufRead")]
    (with-redefs [e.f.sexpr/get-namespace (constantly "foo.bar")
                  e.p.nrepl/current-session (constantly "dummy-session")
                  e.h.evaluate/evaluate-current-buffer (fn [& _])]
      (t/testing "Positive"
        (let [evaluate-current-buffer-called (atom [])]
          (with-redefs [e.h.evaluate/evaluate-current-buffer
                        (fn [& _] (swap! evaluate-current-buffer-called conj :called))]
            (t/is (= ctx (ns-load-enter ctx)))
            (t/is (= [:called] @evaluate-current-buffer-called))

            (t/testing "Do not load when the ns is already loaded"
              (t/is (= ctx (ns-load-enter ctx)))
              (t/is (= [:called] @evaluate-current-buffer-called)))

            (t/testing "Load when the namespace is different"
              (with-redefs [e.f.sexpr/get-namespace (constantly "foo.another")]
                (t/is (= ctx (ns-load-enter ctx)))
                (t/is (= [:called :called] @evaluate-current-buffer-called))
                (t/is (= ctx (ns-load-enter ctx)))
                (t/is (= [:called :called] @evaluate-current-buffer-called))))

            (t/testing "Load when the session is different"
              (with-redefs [e.p.nrepl/current-session (constantly "another-session")]
                (t/is (= ctx (ns-load-enter ctx)))
                (t/is (= [:called :called :called] @evaluate-current-buffer-called))
                (t/is (= ctx (ns-load-enter ctx)))
                (t/is (= [:called :called :called] @evaluate-current-buffer-called)))))))

      (t/testing "Negative"
        (t/testing "Not connected"
          (with-redefs [e.u.interceptor/connected? (constantly false)
                        e.f.sexpr/get-namespace h/must-not-be-called]
            (t/is (= ctx (ns-load-enter ctx)))))

        (t/testing "autocmd-type is not BufRead or BufEnter"
          (with-redefs [e.f.sexpr/get-namespace h/must-not-be-called]
            (let [ctx' (assoc ctx :autocmd-type "Another")]
              (t/is (= ctx' (ns-load-enter ctx'))))))))))

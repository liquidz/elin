(ns elin.interceptor.nrepl.malli-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.interceptor.nrepl.malli :as sut]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(t/deftest malli-lookup-test
  (let [leave-malli-lookup (:leave sut/lookup-schema)
        base-ctx {:request {:op e.c.nrepl/info-op}}
        generate-context (fn [function-schema]
                           (-> (h/nrepl-eval-config (constantly (pr-str function-schema)))
                               (h/test-elin)
                               (merge base-ctx)
                               (assoc :request {:op e.c.nrepl/info-op}
                                      :response [{:ns "foo" :name "bar" :doc "baz"}])))
        get-document-expr (fn [ctx]
                            (let [[_ expr-str] (str/split (get-in ctx [:response 0 :doc]) #"\n+" 2)]
                              (read-string expr-str)))]
    (t/testing "Not found"
      (t/is (nil? (-> (generate-context nil)
                      (dissoc :response)
                      (leave-malli-lookup)
                      (:response)))))


    (t/testing "No function schema"
      (let [ctx (generate-context {})
            res (with-redefs [sut/document-str pr-str]
                  (leave-malli-lookup ctx))]
        (t/is (nil? (get-document-expr res)))))

    (t/testing "Function schema"
      (t/testing "=>"
        (let [ctx (generate-context '[:=> [:cat string?] int?])
              res (with-redefs [sut/document-str pr-str]
                    (leave-malli-lookup ctx))]
          (t/is (= '[{:input [string?] :output int?}]
                   (get-document-expr res)))))

      (t/testing "->"
        (let [ctx (generate-context '[:-> string? int?])
              res (with-redefs [sut/document-str pr-str]
                    (leave-malli-lookup ctx))]
          (t/is (= '[{:input [string?] :output int?}]
                   (get-document-expr res)))))

      (t/testing ":function"
        (let [ctx (generate-context '[:function
                                      [:=> [:cat string?] int?]
                                      [:-> string? int?]])
              res (with-redefs [sut/document-str pr-str]
                    (leave-malli-lookup ctx))]
          (t/is (= '[{:input [string?] :output int?}
                     {:input [string?] :output int?}]
                   (get-document-expr res))))))

    (t/testing "Function parameter"
      (t/testing ":map"
        (let [ctx (generate-context '[:-> [:map [:s string?] [:nested [:map [:k keyword?]]]] :nil])
              res (with-redefs [sut/document-str pr-str]
                    (leave-malli-lookup ctx))]
          (t/is (= '[{:input [{:s string? :nested {:k keyword?}}]
                      :output :nil}]
                   (get-document-expr res)))))

      (t/testing ":sequential"
        (let [ctx (generate-context '[:-> [:sequential string?] :nil])
              res (with-redefs [sut/document-str pr-str]
                    (leave-malli-lookup ctx))]
          (t/is (= '[{:input [[string?]]
                      :output :nil}]
                   (get-document-expr res)))))

      (t/testing ":or"
        (let [ctx (generate-context '[:-> [:or string? keyword?] :nil])
              res (with-redefs [sut/document-str pr-str]
                    (leave-malli-lookup ctx))]
          (t/is (= '[{:input [(or string? keyword?)]
                      :output :nil}]
                   (get-document-expr res))))))))

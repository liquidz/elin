(ns elin.handler.connect-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.handler.connect :as sut]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(t/deftest connect-test
  (t/testing "Positive"
    (let [{:as context :component/keys [host]} (-> (h/test-elin)
                                                   (assoc-in [:message :params] ["localhost" 1234]))
          connected (atom [])]
      (with-redefs [e.p.nrepl/get-client (constantly nil)
                    e.p.nrepl/add-client! (fn [_ {:keys [host port]}]
                                            (swap! connected conj {:host host :port port})
                                            true)
                    e.p.nrepl/switch-client! (constantly true)]
        (sut/connect context))

      (t/is (= [{:host "localhost" :port 1234}]
               @connected))
      (t/is (= ["Connected to localhost:1234"]
               (h/get-outputs host)))))

  (t/testing "Negative"
    (t/testing "No hostname and port"
      (t/testing "Only port"
        (let [{:as context :component/keys [host]} (-> (h/test-elin)
                                                       (assoc-in [:message :params] [1234]))]
          (with-redefs [e.p.nrepl/get-client (constantly nil)
                        e.p.nrepl/add-client! (fn [& _] (throw (ex-info "Must not called" {})))]
            (sut/connect context))

          (t/is (str/starts-with? (first (h/get-outputs host))
                                  "Host or port is not specified:"))))

      (t/testing "No parameter"
        (let [{:as context :component/keys [host]} (h/test-elin)]
          (with-redefs [e.p.nrepl/get-client (constantly nil)
                        e.p.nrepl/add-client! (fn [& _] (throw (ex-info "Must not called" {})))]
            (sut/connect context))

          (t/is (str/starts-with? (first (h/get-outputs host))
                                  "Invalid parameter")))))

    (t/testing "Already connected"
      (let [{:as context :component/keys [host]} (-> (h/test-elin)
                                                     (assoc-in [:message :params] ["localhost" 1234]))]
        (with-redefs [e.p.nrepl/get-client (constantly (h/test-nrepl-client {}))
                      e.p.nrepl/add-client! (fn [& _] (throw (ex-info "Must not called" {})))]
          (sut/connect context))

        (t/is (= ["Already connected to localhost:1234"]
                 (h/get-outputs host)))))))

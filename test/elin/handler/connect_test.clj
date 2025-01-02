(ns elin.handler.connect-test
  (:require
   [clojure.string :as str]
   [clojure.test :as t]
   [elin.constant.jack-in :as e.c.jack-in]
   [elin.function.jack-in :as e.f.jack-in]
   [elin.function.select :as e.f.select]
   [elin.handler.connect :as sut]
   [elin.message :as e.message]
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

(t/deftest instant-test
  (let [context (h/test-elin)]
    (t/testing "Selected project"
      (t/testing "Positive"
        (let [context (assoc-in context [:message :params] [""])
              launched (atom [])
              connected (atom [])
              test-project "test-project"
              test-port (rand-int 10000)]
          (with-redefs [e.f.select/select-from-candidates (constantly test-project)
                        e.f.jack-in/launch-process (fn [_ {:keys [forced-project]}]
                                                     (swap! launched conj forced-project)
                                                     {:port test-port :language (name forced-project)})
                        sut/connect* (fn [_ params]
                                       (swap! connected conj params))]
            (t/is (= [{:hostname "localhost"
                       :port test-port
                       :language test-project
                       :wait? true}]
                     (sut/instant context)))

            (t/is (= [{:hostname "localhost"
                       :port test-port
                       :language test-project
                       :wait? true}]
                     @connected))

            (t/is (= [(keyword test-project)]
                     @launched)))))

      (t/testing "Negative"
        (t/testing "No selection"
          (let [context (assoc-in context [:message :params] [""])
                error (atom [])]
            (with-redefs [e.f.select/select-from-candidates (constantly nil)
                          e.message/error (fn [_ & texts] (swap! error concat texts) nil)]
              (t/is (nil? (sut/instant context)))
              (t/is (= ["Invalid parameter" "No project is selected."]
                       @error)))))))

    (t/testing "Specified project"
      (t/testing "Positive"
        (let [context (assoc-in context [:message :params] [(name e.c.jack-in/babashka)])
              launched (atom [])
              connected (atom [])
              test-port (rand-int 10000)]
          (with-redefs [e.f.jack-in/launch-process (fn [_ {:keys [forced-project]}]
                                                     (swap! launched conj forced-project)
                                                     {:port test-port :language "clojure"})
                        sut/connect* (fn [_ params]
                                       (swap! connected conj params))]
            (t/is (= [{:hostname "localhost"
                       :port test-port
                       :language "clojure"
                       :wait? true}]
                     (sut/instant context)))

            (t/is (= [{:hostname "localhost"
                       :port test-port
                       :language "clojure"
                       :wait? true}]
                     @connected))

            (t/is (= [e.c.jack-in/babashka]
                     @launched)))))

      (t/testing "Negative"
        (t/testing "Invalid project"
          (let [context (assoc-in context [:message :params] ["invalid project"])
                error (atom [])]
            (with-redefs [e.message/error (fn [_ & texts] (swap! error concat texts) nil)]
              (t/is (nil? (sut/instant context)))
              (t/is (= "Invalid parameter"
                       (first @error))))))))))

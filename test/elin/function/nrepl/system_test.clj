(ns elin.function.nrepl.system-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.function.nrepl.system :as sut]
   [elin.protocol.nrepl :as e.p.nrepl]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :each h/test-nrepl-server-port-fixture)

(defn test-handler [session msg]
  (when (= "clone" (:op msg))
    [{:new-session session}]))

(t/deftest get-system-info-test
  (let [{:as sys :keys [nrepl]} (-> (e.system/new-system)
                                    (select-keys [:interceptor :nrepl :plugin :lazy-writer])
                                    (component/start-system))]
    (try
      (let [client (e.p.nrepl/add-client! nrepl "localhost" h/*nrepl-server-port*)]
        (e.p.nrepl/switch-client! nrepl client))

      (let [{:as info :keys [user-dir file-separator project-name]} (sut/get-system-info nrepl)]
        (t/is (and (string? user-dir) (seq user-dir)))
        (t/is (and (string? file-separator) (seq file-separator)))
        (t/is (= "vim-elin" project-name))

        (t/testing "Try to check if the result is cached"
          (let [session (e.p.nrepl/current-session nrepl)
                dummy-nrepl (h/test-nrepl {:client {:handler (partial test-handler session)}})]
            (t/is (= info (sut/get-system-info dummy-nrepl))
                  "Result should be cached")
            (t/is (= user-dir (sut/user-dir dummy-nrepl)))
            (t/is (= file-separator (sut/file-separator dummy-nrepl)))
            (t/is (= project-name (sut/project-name dummy-nrepl))))))

      (finally
        (component/stop-system sys)))))

(ns elin.component.handler-test
  (:require
   [clojure.test :as t]
   [com.stuartsierra.component :as component]
   [elin.component.interceptor :as e.c.interceptor]
   [elin.protocol.interceptor :as e.p.interceptor]
   [elin.protocol.rpc :as e.p.rpc]
   [elin.system :as e.system]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)

(def ^:private test-global-interceptor
  {:name ::test-global-interceptor
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x (partial + 1)))})

(def ^:private test-configured-interceptor
  {:name ::test-configured-interceptor
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x (partial + 2)))})

(def ^:private test-requst-interceptor
  {:name ::test-requst-interceptor
   :kind ::test
   :enter (fn [ctx]
            (update ctx :x (partial + 4)))})

(defn- test-handler
  [{:component/keys [host] :keys [message]}]
  (e.p.rpc/echo-text host (str "Hello " (first (:params message))))
  "OK")

(defn test-global-interceptor-handler
  [{:component/keys [interceptor]}]
  (:x (e.p.interceptor/execute interceptor ::test {:x 0})))

(defn test-configured-interceptor-handler
  [{:component/keys [interceptor]}]
  (:x (e.p.interceptor/execute interceptor ::test {:x 10})))

(def ^:private test-config
  {:handler {:includes [(symbol #'test-handler)
                        (symbol #'test-global-interceptor-handler)
                        (symbol #'test-configured-interceptor-handler)]
             :config-map {(symbol #'test-configured-interceptor-handler)
                          {:interceptor {:includes [(symbol #'test-configured-interceptor)]}}}}
   :interceptor {:includes [(symbol #'test-global-interceptor)]}})

(defn- call-test-handler
  ([handler-component var']
   (call-test-handler handler-component var' []))
  ([handler-component var' params]
   (call-test-handler handler-component var' params {}))
  ([handler-component var' params options]
   (let [handler-fn (:handler handler-component)
         method (str (symbol var'))]
     (handler-fn (h/test-message [0 1 method [params options]])))))

(t/deftest new-handler-test
  (with-redefs [e.c.interceptor/interceptor-group (constantly (deref #'e.c.interceptor/valid-group))]
    (let [{:as sys :keys [handler lazy-host]} (-> (e.system/new-system test-config)
                                                  (dissoc :server :http-server)
                                                  (component/start-system))
          call-test-handler' (partial call-test-handler handler)
          host (h/test-host {:handler (constantly true)})]
      (try
        (e.p.rpc/set-host! lazy-host host)

        (t/testing "Normal handler"
          (let [res (call-test-handler' #'test-handler ["world"])]
            (t/is (= "OK" res))
            (t/is (= ["Hello world"] (h/get-outputs host)))))

        (t/testing "Handler using interceptor"
          (t/testing "Global interceptor"
            (t/is (= 1 (call-test-handler' #'test-global-interceptor-handler))))

          (t/testing "Configured interceptor"
            (t/is (= 13 (call-test-handler' #'test-configured-interceptor-handler))))

          (t/testing "Requested interceptor"
            (let [options {:config (pr-str {:interceptor {:includes [(symbol #'test-requst-interceptor)]}})}]
              (t/is (= 5 (call-test-handler' #'test-global-interceptor-handler [] options)))
              (t/is (= 17 (call-test-handler' #'test-configured-interceptor-handler [] options))))))

        (finally
          (component/stop-system sys))))))

(ns elin.interceptor.nrepl-test
  (:require
   [clojure.test :as t]
   [elin.interceptor.nrepl :as sut]
   [elin.protocol.host :as e.p.host]
   [elin.test-helper :as h]))

(t/use-fixtures :once h/malli-instrument-fixture)
(t/use-fixtures :once h/warn-log-level-fixture)

(def ^:private cider-nrepl-status-message-leave
  (:leave sut/cider-nrepl-status-message))

(t/deftest cider-nrepl-status-message-test
  (let [ctx (assoc (h/test-elin)
                   :message {:coords {:artifact "s3" :group "software.amazon.awssdk" :version "2.28.23"}
                             :status ["download-sources-jar"]})]
    (t/testing "Positive"
      (let [outputs (atom [])]
        (with-redefs [e.p.host/echo-message (fn [_ text]
                                              (swap! outputs conj text))]
          (t/is (= ctx
                  (cider-nrepl-status-message-leave ctx))))
        (t/is (= ["Downloading sources: software.amazon.awssdk/s3@2.28.23"]
                 @outputs))))

    (t/testing "Negative"
      (t/testing "done status"
        (let [outputs (atom [])
              ctx' (assoc-in ctx [:message :status] ["done"])]
          (with-redefs [e.p.host/echo-message (fn [_ text]
                                                (swap! outputs conj text))]
            (t/is (= ctx'
                    (cider-nrepl-status-message-leave ctx'))))
          (t/is (empty? @outputs)))))))

(ns elin.util.nrepl
  (:require
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m])
  (:import
   (java.net ServerSocket)))

(def ^:private ?Messages
  [:sequential e.s.nrepl/?Message])

(defn get-free-port
  []
  (with-open [sock (ServerSocket. 0)]
    (.getLocalPort sock)))

(m/=> merge-messages [:=> [:cat ?Messages] e.s.nrepl/?Message])
(defn merge-messages
  [messages]
  (let [array-keys (map keyword e.c.nrepl/array-key-set)
        array-res (reduce (fn [accm k]
                            (if-let [arr (some->> (keep k messages)
                                                  (seq)
                                                  (apply concat)
                                                  (distinct))]
                              (assoc accm k arr)
                              accm))
                          {} array-keys)]
    (->> (map #(apply dissoc % array-keys) messages)
         (apply merge array-res))))

(m/=> update-messages [:=> [:cat keyword? fn? ?Messages] ?Messages])
(defn update-messages
  [k f messages]
  (loop [[msg & rest-msg] messages
         result []
         changed? false]
    (cond
      (not msg)
      (if changed?
        result
        (conj result {k (f nil)}))

      (contains? msg k)
      (recur rest-msg
             (conj result (update msg k f))
             true)

      :else
      (recur rest-msg (conj result msg) changed?))))

(m/=> has-status? [:=> [:cat e.s.nrepl/?Message string?] boolean?])
(defn has-status?
  [message status]
  (boolean
    (some #(= % status)
          (:status message))))

(m/=> progress-text [:=> [:cat string?] string?])
(defn progress-text
  [op]
  (condp = op
    e.c.nrepl/eval-op "Evaluating..."
    e.c.nrepl/load-file-op "Loading..."
    e.c.nrepl/test-var-query-op "Testing..."
    e.c.nrepl/reload-op "Reloading..."
    e.c.nrepl/reload-all-op "Reloading all..."
    "Processing..."))

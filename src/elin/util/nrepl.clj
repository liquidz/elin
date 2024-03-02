(ns elin.util.nrepl
  (:require
   [elin.constant.nrepl :as e.c.nrepl]
   [elin.schema.nrepl :as e.s.nrepl]
   [malli.core :as m]))

(def ^:private ?Messages
  [:sequential e.s.nrepl/?Message])

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

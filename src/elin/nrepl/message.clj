(ns elin.nrepl.message
  (:require
   [elin.schame.nrepl :as e.s.nrepl]
   [elin.util.schema :as e.u.schema]
   [malli.core :as m]))

(def ^:private array-key-set
  #{"status" "sessions" "classpath"})

(m/=> bytes->str [:=> [:cat any?] e.u.schema/?NotBytes])
(defn- bytes->str
  [x]
  (if (bytes? x)
    (String. (bytes x))
    x))

(m/=> format-message [:=> [:cat [:map-of string? any?]] e.s.nrepl/?Message])
(defn format-message
  [msg]
  (reduce-kv
   (fn [accm k v]
     (assoc accm
            (keyword k)
            (cond
              (contains? array-key-set k)
              (mapv bytes->str v)

              (map? v)
              (format-message v)

              :else
              (bytes->str v))))
   {}
   msg))

(m/=> merge-messages [:=> [:cat [:sequential e.s.nrepl/?Message]] e.s.nrepl/?Message])
(defn merge-messages
  [msgs]
  (let [array-keys (map keyword array-key-set)
        array-res (reduce (fn [accm k]
                            (if-let [arr (some->> (keep k msgs)
                                                  (seq)
                                                  (apply concat)
                                                  (distinct))]
                              (assoc accm k arr)
                              accm))
                          {} array-keys)]
    (->> (map #(apply dissoc % array-keys) msgs)
         (apply merge array-res))))

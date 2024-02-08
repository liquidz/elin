(ns elin.util.function)

(defn memoize-by
  [key-fn f]
  (let [mem (atom {})]
    (fn [& args]
      (when-let [k (key-fn args)]
        (if-let [e (find @mem k)]
          (val e)
          (let [ret (apply f args)]
            (swap! mem assoc k ret)
            ret))))))

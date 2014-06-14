(ns ^{:author "Frank V. Castellucci"
      :doc "tributary - Parse BPMN and XPDL resources"}
  tributary.core
  (:require [tributary.tribpers :refer :all]
            [tributary.utils :as tu]
            [tributary.bpmn :as bpmn]
            [tributary.xpdl :as xpdl]
            )
  )

(def ^:dynamic *default-persist-store* tributary.tribpers/store)
(defn set-default-persist-store!
  "Changes the default persist function"
  [function-reference]
  {:pre [(fn? function-reference)]}
  (alter-var-root #'*default-persist-store* (constantly function-reference)))

(def ^:dynamic *default-persist-fetch* tributary.tribpers/fetch)
(defn set-default-persist-fetch!
  "Changes the default persist fetch function"
  [function-reference]
  {:pre [(fn? function-reference)]}
  (alter-var-root #'*default-persist-fetch* (constantly function-reference)))

(defn context-from-source
  "Returns the appropriate context (BPMN or XPDL) based on source input"
  [source-input]
  (let [_h (tu/parse-source source-input)]
    (if (= (:stype _h) :bpmn) (bpmn/context _h) (xpdl/context _h))))


(comment
  (def _t0 (context-from-source
           (-> "Incident Management.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (def _t0 (context-from-source
            (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (use 'clojure.pprint)

  (pprint (map #(assoc {} (first %) (count (second %))) _t0))
  (pprint (map #(assoc {}
                  :p-nodes   (count (:process-nodes %))
                  :p-flows   (count (:process-flows %))
                  :p-data    (count (:process-data %))
                  :p-objs    (count (:process-object-refs %))
                  :p-stores  (count (:process-store-refs %))
                  :p-flwrefs (count (:process-flow-refs %))
                  )
               (:processes _t0)))

  (pprint (:definition _t0))
  (pprint (:data-stores _t0))
  (pprint (:items _t0))
  (pprint (:resources _t0))
  (pprint (:messages _t0))
  (pprint (:interfaces _t0))
  (pprint (:processes _t0))

  (pprint (:process-store-refs (nth (:processes _t0) 0)))
  (pprint (:process-object-refs (nth (:processes _t0) 0)))
  (pprint (:process-flow-refs (nth (:processes _t0) 0)))
  (pprint (:process-nodes (nth (:processes _t0) 0)))
  (pprint (:process-flows (nth (:processes _t0) 0)))
  (pprint (:process-data (nth (:processes _t0) 0)))
  )

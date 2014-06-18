(ns ^{:author "Frank V. Castelluci"
      :doc "Development only spikes"}
  tributary.user
  (:require [tributary.bpmn :refer :all]
            [clojure.zip :as zip]
            [tributary.utils :as tu]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx]
            [clojure.pprint :refer :all]
   ))


  (def _s0 (tu/parse-source (-> "Email Voting.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (time (def _t0 (context _s0)))
  (def _n0 (:process-nodes (nth (:processes _t0) 0)))

  (pprint _n0)
  (pprint (tu/nodes-for-type :semantic:userTask _n0))
  (pprint (:process-flows (nth (:processes _t0) 0)))

;--------------------------------------------
(comment
  (def _s0 (tu/parse-source (-> "Incident Management.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (tu/parse-source (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (tu/parse-source (-> "Hardware Retailer.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (tu/parse-source (-> "Order Fulfillment.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (tu/parse-source (-> "Travel Booking.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (tu/parse-source (-> "Email Voting.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (time (def _t0 (context _s0)))
  (count (:processes _t0))
  (def _p0 (zx/xml1-> (:zip _s0) :semantic:process))
  (pprint (mapv #(select-keys (:attrs %) [:sourceRef :targetRef])
                (zx/xml-> _p0 :semantic:sequenceFlow zip/node)))

  (pprint (map #(assoc {} (first %) (count (second %))) _t0))
  (pprint (map #(assoc {}
                  :p-nodes   (count (:process-nodes %))
                  :p-flows   (count (:process-flows %))
                  :p-data    (count (:process-data %))
                  :p-objs    (count (:process-object-refs %))
                  :p-stores  (count (:process-store-refs %))
                  :p-flwrefs (count (:process-flow-refs %))
                  :p-procs   (count (:processes %))
                  )
               (:processes _t0)))

  (pprint (map (fn [node]
                 (apply merge (map #(let [_sn (second %)
                             _c (if (or (vector? _sn) (map? _sn))
                                  (count _sn)
                                  _sn)]
                         (assoc {} (first %) _c)) node)))
               (:process-nodes (nth (:processes _t0) 0))))

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

  (defn map-zipper
    [m]
    (zip/zipper
     (fn [x] (let [_r (or (map? (second x)) (vector? (second x)))]
               _r))
     (fn [x] (seq (if (map? x) x (nth x 1))))
     (fn [x children]
       (if (map? x)
         (into {} children)
         (assoc x 1 (into {} children))))
     m))

  (pprint (time (-> _z0 zip/next zip/right zip/children)))
  (pprint (time (seq (:messages _t0))))
  (count (zx/xml-> _z0 (_m :semantic:message)))
  )


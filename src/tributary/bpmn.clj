(ns tributary.bpmn
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx])
  )

;; Data setup
(defn- dtype
  [node]
  (let [_x (clojure.string/split node #":")]
    (assoc {} :lang (_x 0) :ref (_x 1))))

(defn- dataset-def
  [ls-items]
  (for [_x ls-items
        :let [_xn (:attrs (zip/node _x))
              _y (assoc {} (keyword (:id _xn))
                   (dtype (:structureRef _xn)))]]
    _y))

(defn- dataset-objdef
  [ls-items]
  (for [_x ls-items
        :let [_xn (:attrs (zip/node _x))
              _y (assoc {} (keyword ( :itemSubjectRef _xn))
                   {:name (:name _xn)
                    :data-objectref-id (:id _xn)
                    :collection (:isCollection _xn)})]]
    _y))

(defn- dataset-inpdef
  [ls-items]
  (for [_x ls-items
        :let [_xn (:attrs (zip/node _x))
              _y (assoc {} (keyword (:itemSubjectRef _xn))
                   {:data-inputref-id (:id _xn)})]]
   _y))

(defn- data-context
  [zip]
  (let [dd  (apply merge (dataset-def (zx/xml-> zip :model:itemDefinition )))
        do  (apply merge (dataset-objdef (zx/xml-> zip :model:process :model:dataObject )))
        di  (apply merge (dataset-inpdef (zx/xml-> zip :model:process :model:ioSpecification :model:dataInput)))
        ds  (map #(assoc {} (key %) (merge (val %) (val %2) (val %3))) dd do di)]
    (assoc {} :dataset (apply merge ds))))

;; Process setup
; Each lane in the lane set represents a flow

(defn lane-seq
  [lid node header]
  (assoc-in header [lid :steps]
            (into [] (map #(keyword (first (:content %))) (:content node)))))

(defn lane-defs
  [ls-items]
  (for [ln ls-items
        :let [_n0 (zip/node ln)
              _na (:attrs _n0)
              _ni (keyword (:id _na))
              lh  (assoc {} _ni {:name (:name _na)})
              ld  (lane-seq _ni _n0 lh)]]
    ld))

(defn- user-task
  [ls-items]
  (for [_x ls-items
        :let [_xn (:attrs (zip/node _x))
              _y (assoc {} :user-task {(keyword (:id _xn)){:name (:name _xn)}})]]
    _y))

(defn- process-context
  [zip]
  (let [ld  (apply merge (lane-defs (zx/xml-> zip :model:process :model:laneSet :model:lane)))
        ut  (apply merge (user-task (zx/xml-> zip :model:process :model:userTask)))
        ;se (zx/xml-> pb :model:)
        ]
    (assoc {} :processes ld)))

(defn pnz
  [input-source]
  (zip/xml-zip (xml/parse input-source)))

(defn context
  [input-source]
  (let [zip (pnz input-source)]
    (conj (data-context zip) (process-context zip))))

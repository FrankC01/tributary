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
              _y (conj (select-keys _xn [:id])
                   (dtype (:structureRef _xn)))]]
    _y))

(defn- dataset-objdef
  [ls-items]
  (for [_x ls-items
        :let [_xn (:attrs (zip/node _x))
              _y   {:id (:itemSubjectRef _xn)
                    :name (:name _xn)
                    :data-objectref-id (:id _xn)
                    :collection (symbol (:isCollection _xn))}]]
    _y))

(defn- dataset-inpdef
  [ls-items]
  (for [_x ls-items
        :let [_xn (:attrs (zip/node _x))
              _y (assoc {}
                   :id (:itemSubjectRef _xn)
                   :data-inputref-id (:id _xn))]]
   _y))

(defn- data-context
  [zip]
  (let [dd   (dataset-def (zx/xml-> zip :model:itemDefinition ))
        dof  (dataset-objdef (zx/xml-> zip :model:process
                                                   :model:dataObject ))
        di   (dataset-inpdef (zx/xml-> zip :model:process
                                                   :model:ioSpecification
                                                   :model:dataInput))]
    (assoc {} :datarefs (into [] (map merge dd dof di)))))

;; Process setup
; Each lane in the laneset represents a flow

(defn- seq-def
  [flows content]
  (for [_t flows
        :let [_y (first (filter #(= (get-in % [:attrs :id]) _t)content))
              _x (assoc {} :id _t
                   :spec-type (keyword (:ref (dtype(name (:tag _y)))))
                   :name (get-in _y [:attrs :name]))]]
    _x))

(defn- lane-seq
  [node header zip]
  (let [_n (into [] (map #(first (:content %)) (:content node)))
        _c (zip/children (first (zx/xml-> zip :model:process)))
        _o (into [] (seq-def _n _c))]
  (assoc-in header [:flow] _o)))

(defn- lane-defs
  [ls-items zip]
  (for [ln ls-items
        :let [_n0 (zip/node ln)
              _lh  (select-keys (:attrs _n0) [:id :name])
              _ld  (lane-seq _n0 _lh zip)]]
     _ld))


(defn- process-context
  [zip]
  (let [ld  (into [] (lane-defs (zx/xml-> zip :model:process :model:laneSet
                                              :model:lane) zip))
        ;se (zx/xml-> pb :model:)
        ]
    (assoc {} :processes ld)))

(defn- pnz
  [input-source]
  (zip/xml-zip (xml/parse input-source)))

(defn context
  [input-source]
  (let [zip (pnz input-source)]
    (conj (data-context zip) (process-context zip))))


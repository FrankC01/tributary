(ns tributary.bpmn
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx])
  )

;; Bindings
(def ^:dynamic ^:private *zip* nil)
(def ^:dynamic ^:private *prefix* nil)

;; Utility

(defn- pf
  [kw]
  (keyword (str *prefix* kw)))

(defn- dtype
  [node]
  (let [_x (clojure.string/split node #":")]
    (assoc {} :lang (_x 0) :ref (_x 1))))

(defn- -?
  [pred form1 form2]
  (if pred form1 form2))

;; Data setup

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
  "Develop the data reference context details"
  []
  (let [dd   (dataset-def (zx/xml-> *zip* (pf :itemDefinition) ))
        dod  (dataset-objdef (zx/xml-> *zip* (pf :process)
                                             (pf :dataObject)))
        did   (dataset-inpdef (zx/xml-> *zip* (pf :process)
                                        (pf :ioSpecification)
                                        (pf :dataInput)))]
    (assoc {} :datarefs (into [] (map merge dd dod did)))))

;; Process setup
; Each lane in the laneset represents a flow

(defn- seq-def
  "General purpose lane flow type information"
  [flows content]
  (for [_t flows
        :let [_y (first (filter #(= (get-in % [:attrs :id]) _t)content))
              _x (assoc {} :id _t
                   :spec-type (keyword (:ref (dtype(name (:tag _y)))))
                   :name (get-in _y [:attrs :name]))]]
    _x))

(defn- seq-condition
  "Sets up the conditional expression, typically for a gateway"
  [node]
  (conj (dtype (:evaluatesToTypeRef (:attrs node)))
        {:expression (or (:content node) :any)}))

(defn- seq-ast
  "seq-ast recurses through the steps and branches of a flow"
  [seq-item s-defs]
  (let [_n (zip/node seq-item)
        _s (first (filter #(= (:sourceRef (:attrs _n)) (:id %)) s-defs))
        _t (first (filter #(= (:targetRef (:attrs _n)) (:id %)) s-defs))
        _c (-? (nil? (:content _n)) [] (into [] (map seq-condition (:content _n))))
        _f (zx/xml-> *zip* (pf :process) (pf :sequenceFlow)
                             (zx/attr= :sourceRef (:id _t)))]
    (assoc _s :condition _c
      :step (-? (empty? _f) [_t] (into [] (map #(seq-ast % s-defs) _f))))))

(defn- lane-seq
  "Builds the sequence of steps"
  [node header]
  (let [_n  (into [] (map #(first (:content %)) (:content node)))
        _o  (into [] (seq-def _n (zip/children (first (zx/xml-> *zip* (pf :process))))))
        _s  (first (filter #(= (:spec-type %) :startEvent) _o))]
    (assoc-in header [:flow]
              (seq-ast (zx/xml1-> *zip* (pf :process)
                                  (pf :sequenceFlow)
                                  (zx/attr= :sourceRef (:id _s))) _o))))

(defn- lane-defs
  "Builds the individual lanes"
  [ls-items]
  (for [ln ls-items
        :let [_n0 (zip/node ln)
              _ld  (lane-seq _n0 (select-keys (:attrs _n0) [:id :name]))]]
     _ld))


(defn- process-context
  "Builds out the processes defined in lanesets"
  []
  (assoc {} :processes
    (into [] (lane-defs (zx/xml-> *zip* (pf :process) (pf :laneSet) (pf :lane))))))


(defn context
  [parse-block]
  (binding [*zip* (:zip parse-block) *prefix* (:ns parse-block)]
    (conj (data-context) (process-context))))


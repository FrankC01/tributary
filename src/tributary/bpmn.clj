(ns tributary.bpmn
  (:require [clojure.zip :as zip]
            [tributary.utils :as tu]
            [clojure.xml :as xml]
            [clojure.set :as set]
            [clojure.data.zip.xml :as zx])
  )

;; Bindings
(def ^:dynamic ^:private *zip* nil)
(def ^:dynamic ^:private *prefix* nil)

;; Utility

(defn- pf
  "Forms fully qualified keyword considering xmlns prefix"
  [kw]
  (keyword (str *prefix* kw)))

(defn- dtype
  [node]
  (let [_x (clojure.string/split node #":")]
    (assoc {} :lang (_x 0) :ref (_x 1))))

;; Data setup

(def ^:private data-attrs [:itemSubjectRef :name :isCollection])

(defn- data-name
  "Takes a data id and pulls name and collection indicator"
  [data-id root namekw]
  (set/rename-keys
   (select-keys
    (:attrs (zip/node (zx/xml1-> root (pf namekw) (zx/attr= (first data-attrs) data-id))))
    data-attrs) {(first data-attrs) :id}))


(defn- data-iospec
  "root can be process or task. Takes the result of :ioSpecifiction 'iospec' zip node
  then uses the :itemSubjectRef as :id filter for completing the definition"
  [root namekw]
  (let [_di (zx/xml-> root  (pf :ioSpecification) (pf :dataInput))]
    (mapv #(data-name ((first data-attrs) (:attrs (zip/node %))) root namekw) _di)))

(defn- data-type
  "Takes the current data map, uses ID to search for type information"
  [data-map root typekw]
  (let [_x (:structureRef (:attrs (zip/node (zx/xml1-> root (pf typekw) (zx/attr= :id (:id data-map))))))]
    (conj data-map (dtype _x))))

(defn- global-data
  "Pulls global process definitions"
  []
  (mapv #(data-type %  *zip* :itemDefinition)
        (data-iospec (zx/xml1-> *zip* (pf :process)) :dataObject)))

(defn- data-context
  "Develop the data reference context details"
  []
  (assoc {} :process-data (global-data)))

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
        _c (if (nil? (:content _n)) [] (into [] (map seq-condition (:content _n))))
        _f (zx/xml-> *zip* (pf :process) (pf :sequenceFlow)
                             (zx/attr= :sourceRef (:id _t)))]
    (assoc _s :condition _c
      :step (if (empty? _f) [_t] (into [] (map #(seq-ast % s-defs) _f))))))

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

(use 'clojure.pprint)

(def _s0 (tu/parse-source "resources/Valid Ticket-LD.bpmn"))
(def _t0 (context _s0))
(pprint (:process-data _t0))

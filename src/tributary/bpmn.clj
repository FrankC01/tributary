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
    (condp = (count _x)
      1 (assoc {} :lang nil :ref (_x 1))
      2 (assoc {} :lang (_x 0) :ref (_x 1))
      3 (assoc {} :lang (_x 1) :ref (_x 2))
     )))

;; Data setup

(def ^:private data-attrs [:itemSubjectRef :name :isCollection])

(defn- data-name
  "Takes a data id and pulls name and collection indicator for both locally
  (e.g. :property or globally :dataObject)."
  [data-id root specroot]
  (let [_dg  (zx/xml1-> root (pf :dataObject) (zx/attr= :itemSubjectRef data-id))
        _do  (if (nil? _dg)
               (zx/xml1-> specroot (pf :property) (zx/attr= :itemSubjectRef data-id))
               _dg)]
    (update-in
     (set/rename-keys
      (conj {:isCollection "false"} (select-keys (:attrs (zip/node _do)) data-attrs))
      {:itemSubjectRef :id}) [:isCollection] symbol)))

(defn- data-iospec
  "root can be process or task. Takes the result of :ioSpecifiction 'iospec' zip node
  then uses the :itemSubjectRef as :id filter for completing the definition"
  [specroot root]
  (let [_di (zx/xml-> specroot  (pf :ioSpecification) (pf :dataInput))]
    (mapv #(assoc (data-name (:itemSubjectRef (:attrs (zip/node %))) root specroot)
             :refid (:id (:attrs (zip/node %))))
          _di)))

(defn- data-type
  "Takes the current data map, uses ID to search for type information"
  [data-map root typekw]
  (let [_x (:structureRef (:attrs (zip/node (zx/xml1-> root (pf typekw) (zx/attr= :id (:id data-map))))))]
    (conj data-map (dtype _x))))

(defn- global-data
  "Pulls global process definitions"
  [pnode]
  (mapv #(data-type %  *zip* :itemDefinition)
        (data-iospec pnode pnode)))


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

(defn- task-definition
  "Task data may be global or locally defined.
  If locally defined, it does not have type information"
  [tnode proot]
  (let [_dts   (mapv #(data-type %  *zip* :itemDefinition) (data-iospec tnode proot))
        ]
   (assoc {} :data _dts :execution [])))

(defn- finish-node
  [node node-type proot]
  (condp = node-type
    :startEvent       nil
    :task             (task-definition node proot)
    :userTask         (task-definition node proot)
    :exclusiveGateway nil
    :endEvent         nil
    ))

(defn- node-def
  [node-id proot _cat]
  (let [_t (first (filter #(= (:id %) node-id) _cat))
        _n (zx/xml1-> proot (:type _t) (zx/attr= :id node-id))
        _a (conj _t (:attrs (zip/node _n))
                 {:type (keyword (:ref (dtype (str (:type _t)))))})
        ]
    (conj _a (finish-node _n (:type _a) proot))))

(defn- flow-def
  [flowref proot]
  (let [_fr (:attrs (zip/node flowref))
        _ct (map #(assoc {} :id (:id (:attrs %)) :type (:tag %)) (zip/children proot))
        _fn (map zx/text (zx/xml-> flowref (pf :flowNodeRef)))
        _nd (mapv #(node-def % proot _ct) _fn)
        _sn (first (filter #(= (:type %) :startEvent) _nd))
        _fd (assoc-in _fr [:nodes] _nd)
        ]
    _fd)
  )

(defn- flowset-def
  [flow-set proot]
  (let [_fl (mapv #(flow-def % proot) (zx/xml-> flow-set (pf :lane)))]
    _fl))

(defn- process-def
  [proot]
  (let [_pn (assoc-in (:attrs (zip/node proot)) [:process-data]
                      (global-data proot))
        _pn (assoc-in _pn [:flowsets] (mapv #(flowset-def % proot)
                                           (zx/xml-> proot (pf :laneSet))))
        ]
    _pn))

(defn- process-context
  []
  (assoc {} :processes (mapv #(process-def %) (zx/xml-> *zip* (pf :process)))))

(defn context
  [parse-block]
  (binding [*zip* (:zip parse-block) *prefix* (:ns parse-block)]
    (process-context)))

(comment
  (use 'clojure.pprint)
  (def _s0 (tu/parse-source "resources/Valid Ticket-LD.bpmn"))
  (def _t0 (context _s0))
  (pprint _t0)
  )


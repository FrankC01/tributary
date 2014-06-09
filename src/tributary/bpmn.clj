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

(defn node-for-id
  "Returns first node matching node-id in nodes collection"
  [node-id nodes]
  (first (filter #(= (:id %) node-id) nodes)))

(defn nodes-for-type
  "Returns lazy-sequence of nodes that match the node-type"
  [node-type nodes]
  (filter #(= (:type %) node-type) nodes))

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
  "Pulls global data definitions"
  [pnode]
  (mapv #(data-type %  *zip* :itemDefinition)
        (data-iospec pnode pnode)))


;; Process setup
; Each lane in the laneset represents a flow

(defn- task-data-binding
  "Returns to and from data bindings in task"
  [input-assoc]
  (assoc {}
    :to-data-refid (zx/xml1-> input-assoc (pf :targetRef) zx/text)
    :from (zx/xml1-> input-assoc (pf :assignment) (pf :from) zx/text)))

(defn- task-definition
  "Returns map for tasks local data declarations and data bindings"
  [tnode proot]
  (assoc {}
    :data (mapv #(data-type %  *zip* :itemDefinition) (data-iospec tnode proot))
    :bindings (mapv task-data-binding (zx/xml-> tnode (pf :dataInputAssociation)))))

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

(declare step-def)

(defn- step-ref
  [sqref proot nodes]
  (let [_sqnode  (zip/node sqref)
        _idsmap  (set/rename-keys
                  (select-keys (:attrs _sqnode) [:sourceRef :targetRef :name])
                  {:sourceRef :node :targetRef :next})
        _stptype  (select-keys (node-for-id (:node _idsmap) nodes) [:type])
        _conddref (zx/xml1-> sqref (pf :conditionExpression) zx/text)
        _conds    {:predicates
                   [(cond (or (nil? _conddref) (= _conddref "")) :none
                          :else _conddref)]}]
    (conj _idsmap _stptype _conds {:next (step-def (:next _idsmap) proot nodes) })))

(defn- step-def
  [srcid proot nodes]
  (let [_sx (zx/xml-> proot (pf :sequenceFlow) (zx/attr= :sourceRef srcid))
        _sd (if (empty? _sx)
              [(conj (select-keys (node-for-id srcid nodes) [:type])
                     {:name ""
                      :node srcid
                      :next []
                      :predicates [:none]
                      })]
              (mapv #(step-ref % proot nodes) _sx))
        ]
    _sd))

(defn- flow-def
  [flowref proot]
  (let [_fr (:attrs (zip/node flowref))
        _ct (map #(assoc {} :id (:id (:attrs %)) :type (:tag %)) (zip/children proot))
        _fn (map zx/text (zx/xml-> flowref (pf :flowNodeRef)))
        _nd (mapv #(node-def % proot _ct) _fn)
        _sn (step-def (:id (first (filter #(= (:type %) :startEvent) _nd))) proot _nd)
        _fd (assoc-in _fr [:nodes] _nd )
        _fd (assoc-in _fd [:steps] _sn )
        ]
    _fd))

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
  "Takes a parse block and returns one or more process contexts"
  [parse-block]
  (binding [*zip* (:zip parse-block) *prefix* (:ns parse-block)]
    (process-context)))


(comment
  (use 'clojure.pprint)
  (def _s0 (tu/parse-source "resources/Valid Ticket-LD.bpmn"))
  (def _t0 (context _s0))
  (pprint _t0)
  (keys (ffirst (:flowsets (first (:processes _t0)))))
  )


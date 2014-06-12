(ns ^{:author "Frank V. Castellucci"
      :doc "tributary BPMN parser functions"}
  tributary.bpmn
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
  (if (nil? *prefix*)
    kw
  (keyword (str *prefix* kw))))

(defn- dtype
  [node]
  (let [_x (clojure.string/split node #":")]
    (condp = (count _x)
      1 (assoc {} :ns nil :ref (_x 0))
      2 (assoc {} :ns (_x 0) :ref (_x 1))
      3 (assoc {} :ns (_x 1) :ref (_x 2)))))

;; Data setup

(def ^:private data-attrs [:itemSubjectRef :name :isCollection])
(def ^:private node-ex [:ioSpecification :laneSet :lane :sequenceFlow :dataObject])

(defn- data-object
  [refdata reftype scope]
  (let [_at (conj {:reftype reftype :scope scope} (:attrs (zip/node refdata)))]
    (assoc _at :item-id (dtype (:itemSubjectRef _at)))))

(defn- data-iospec
  "root can be process or task. Takes the result of :ioSpecifiction 'iospec' zip node
  then uses the :itemSubjectRef as :id filter for completing the definition"
  [specroot root]
  (let [_sc (if (= specroot root) :process :task)
        _di (map #(data-object % :input _sc)
                 (zx/xml-> specroot  (pf :ioSpecification) (pf :dataInput)))
        _do (map #(data-object % :output _sc)
                 (zx/xml-> specroot  (pf :ioSpecification) (pf :dataOutput)))
        ]
    (mapv #(dissoc % :itemSubjectRef) (reduce conj _di _do))))

;; Process setup
; Each lane in the laneset represents a flow

(defn- task-data-binding
  "Returns to and from data bindings in task."
  [input-assoc]
  (assoc {}
    :to-data-refid (zx/xml1-> input-assoc (pf :targetRef) zx/text)
    :from (zx/xml1-> input-assoc (pf :assignment) (pf :from) zx/text)))

(defn- task-definition
  "Returns general map for tasks, data declarations and data bindings"
  [tnode proot]
  (assoc {}
    :owner-resource []
    :data  (data-iospec tnode proot)
    :bindings (mapv task-data-binding (zx/xml-> tnode (pf :dataInputAssociation)))))

(defn- userTask-definition
  "Retreives potential owners (resources) of task"
  [tnode proot]
  (let [_mp (task-definition tnode proot)
        _po (zx/xml-> tnode (pf :potentialOwner))]
    (assoc-in _mp [:owner-resource]
              (mapv #(:ref (dtype (zx/xml1-> % (pf :resourceRef) zx/text))) _po))))

(defn- script-task-data-binding
  "Returns to and from data bindings in task."
  [input-assoc]
  (assoc {}
    :to-data-refid (zx/xml1-> input-assoc (pf :targetRef) zx/text)
    :from (zx/xml1-> input-assoc (pf :sourceRef) zx/text)))

(defn- scriptTask-definition
  "Returns map for tasks local data declarations and data bindings"
  [tnode proot]
  (assoc (task-definition tnode proot)
    :bindings (mapv script-task-data-binding (zx/xml-> tnode (pf :dataInputAssociation)))
    :script   (zx/xml1-> tnode (pf :script) zx/text)
    ))

(defn- finish-node
  [node node-type proot]
  (condp = node-type
    :startEvent       nil
    :task             (task-definition node proot)
    :userTask         (userTask-definition node proot)
    :scriptTask       (scriptTask-definition node proot)
    :sendTask         nil
    :serviceTask      nil
    :exclusiveGateway nil
    :endEvent         nil
    (throw (Exception. (str "Invalid node-type " node-type)))
    ))

(defn- node-include?
  [node]
  ((complement some?)
   (some (conj #{} (keyword (:ref (dtype (name (:tag node)))))) node-ex)))

(defn- nodes-only
  [proot]
  (mapv #(conj (assoc (:attrs %) :type (:tag %))
               (finish-node (zx/xml1-> proot (pf (:tag %))
                                     (zx/attr= :id (:id (:attrs %)))) (:tag %) proot))
        (filter node-include? (zip/children proot))))

(defn- node-def
  "Generic node definition parse"
  [node-id proot _cat]
  (let [_t (first (filter #(= (:id %) node-id) _cat))
        _n (zx/xml1-> proot (:type _t) (zx/attr= :id node-id))
        _a (conj _t (:attrs (zip/node _n))
                 {:type (keyword (:ref (dtype (str (:type _t)))))})
        ]
    (conj _a (finish-node _n (:type _a) proot))))

(declare step-def)

(defn- step-ref
  "Step definition helper"
  [sqref proot nodes]
  (let [_sqnode  (zip/node sqref)
        _idsmap  (set/rename-keys
                  (select-keys (:attrs _sqnode) [:sourceRef :targetRef :name])
                  {:sourceRef :node :targetRef :next :name :arc-name})
        _stptype  {:node (select-keys (tu/node-for-id (:node _idsmap) nodes)
                                      [:id :type])}
        _conddref (zx/xml1-> sqref (pf :conditionExpression) zx/text)
        _conds    {:predicates
                   [(cond (or (nil? _conddref) (= _conddref "")) :none
                          :else _conddref)]}]
    (conj _idsmap _stptype _conds {:next (step-def (:next _idsmap) proot nodes)})))

(defn- step-def
  "Parse the sequence flow for step definition"
  [srcid proot nodes]
  (let [_sx (zx/xml-> proot (pf :sequenceFlow) (zx/attr= :sourceRef srcid))
        _sd (if (empty? _sx)
              [{:arc-name ""
                :node {:id srcid :type (:type (tu/node-for-id srcid nodes))}
                :next []
                :predicates [:none]
                }]
              (mapv #(step-ref % proot nodes) _sx))
        ]
    _sd))

; TODO: In lane attributes, the partitionElementRef may be resource ref
; need to either rename or ignore/drop from returns

(defn- node-driver
  [proot nodes]
  (let [_se (map :id (tu/nodes-for-type :startEvent nodes))]
    (first (map #(step-def % proot nodes) _se))))

(defn- process-lane
  [lane]
  (let [_at (:attrs (zip/node lane))]
    (assoc _at :node-refs
      (into [] (zx/xml-> lane (pf :flowNodeRef) zx/text)))))

(defn- process-lanesets
  [laneset]
  (let [_at (:attrs (zip/node laneset))]
    (assoc _at :flow-refs (mapv process-lane (zx/xml-> laneset (pf :lane))))))

(defn- process-def
  "Parse the process data, nodes and steps"
  [proot]
  (let [_flowrefs (mapv process-lanesets (zx/xml-> proot (pf :laneSet)))
        _nodedefs (nodes-only proot)
        _pn (assoc (:attrs (zip/node proot))
              :process-data (data-iospec proot proot)
              :process-flows (node-driver proot _nodedefs))]
    (assoc _pn :process-nodes _nodedefs :process-flow-refs _flowrefs)))

(defn- interface-op
  [op]
  (assoc (:attrs (zip/node op)) :messages
             (mapv #(assoc {} :msg-id %)
                   (zx/xml-> op (pf :inMessageRef) zx/text))))

(defn- interface-def
  [ifaceref]
  (let [_at (set/rename-keys (:attrs (zip/node ifaceref))
                     {:implementationRef :implementation})
        _at (assoc _at :implementation (dtype (:implementation _at)))
        ]
    (assoc _at :operations (mapv interface-op (zx/xml-> ifaceref (pf :operation))))))

(defn- items
  [itmdef]
  (let [_at (conj {:isCollection "false"} ((comp :attrs zip/node) itmdef))]
    (assoc _at :structureRef (dtype (:structureRef _at))
                :isCollection (symbol (:isCollection _at)))))

(defn- process-context
  "For each process, parse a process definition context"
  []
  (let [_cntx (assoc {} :processes
                (mapv #(process-def %) (zx/xml-> *zip* (pf :process))))
        _cntx (assoc-in _cntx [:interfaces]
                        (mapv interface-def (zx/xml-> *zip* (pf :interface))))
        _cntx (assoc-in _cntx [:resources]
                        (mapv #(:attrs (zip/node %))
                              (zx/xml-> *zip* (pf :resource))))
        _cntx (assoc-in _cntx [:messages]
                        (mapv #(:attrs (zip/node %))
                              (zx/xml-> *zip* (pf :message))))]
    (assoc-in _cntx [:items] (mapv items (zx/xml-> *zip* (pf :itemDefinition))))))

(defn context
  "Takes a parse block and returns process contexts"
  [parse-block]
  (binding [*zip* (:zip parse-block)
            *prefix* (:ns parse-block)]
    (process-context)))

;--------------------------------------------
(comment
  (use 'clojure.pprint)
  (def _s0 (tu/parse-source "resources/Incident Management.bpmn"))
  (def _t0 (context _s0))

  (pprint (:items _t0))
  (pprint (:resources _t0))
  (pprint (:messages _t0))
  (pprint (:interfaces _t0))
  (pprint (:processes _t0))

  (pprint (:process-flow-refs (first (:processes _t0))))
  (pprint (:process-nodes (first (:processes _t0))))
  (pprint (:process-flows (first (:processes _t0))))
  (pprint (:process-data (first (:processes _t0))))

  )


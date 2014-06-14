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

(defn- kw-to-refkw
  [kw]
  (keyword (:ref (dtype (str kw)))))

;; Data setup

(def ^:private data-attrs [:itemSubjectRef :name :isCollection])
(def ^:private node-ex [:ioSpecification :laneSet :lane :sequenceFlow :dataObject
                        :dataObjectReference :dataStoreReference
                        :textAnnotation :association])

(defn- data-object
  [refdata reftype scope]
  (let [_at (conj {:reftype reftype :scope scope} (:attrs (zip/node refdata)))
        _at (assoc _at :item-id (if (nil? (:itemSubjectRef _at))
                       nil
                       (dtype (:itemSubjectRef _at))))]
    _at))

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

(defn- type-io
  [node]
  (let [_da   (reduce conj (zx/xml-> node (pf :dataInputAssociation))
                      (zx/xml-> node (pf :dataOutputAssociation)))
        _sv   (mapv #(assoc {} :from (zx/xml1-> % (pf :sourceRef) zx/text)
                       :to (zx/xml1-> % (pf :targetRef) zx/text)) _da)
        _sa   (into [] (flatten (mapv #(map (fn [anode]
                            (assoc {} :to (zx/xml1-> anode :to zx/text)
                                :from (zx/xml1-> anode :from zx/text)))
                            (zx/xml-> % :assignment)) _da)))]
    (assoc {} :bindings _sv :assignment _sa)))

(defn- type-data-definition
  "Returns general map for tasks, events, etc."
  [tnode proot]
  (conj (assoc {}
    :owner-resource []
    :data  (data-iospec tnode proot)) (type-io tnode)))

(defn- task-loops
  "Currently just the attributes of the node. BPMN 2.0 declares that conformance
  includes expressions, events, inputs and outputs"
  [tnode]
  (mapv #(conj (:attrs (zip/node %)) {:type (:tag (zip/node %))})
        (reduce conj (zx/xml-> tnode (pf :standardLoopCharacteristics))
          (zx/xml-> tnode (pf :multiInstanceLoopCharacteristics)))))

(defn- task-definition
  "Retreives potential owners (resources) of task"
  [tnode proot]
  (let [_mp (type-data-definition tnode proot)
        _mp (assoc _mp :owner-resource
                      (mapv #(:ref (dtype (zx/xml1-> % (pf :resourceRef) zx/text)))
                            (zx/xml-> tnode (pf :potentialOwner))))
        _mp (assoc _mp :loop (task-loops tnode))
        _mp (assoc _mp :script (zx/xml1-> tnode (pf :script) zx/text))]
    _mp))

(def ^:private evdefs [:messageEventDefinition :timerEventDefinition
                       :cancelEventDefinition :compensationEventDefinition
                       :conditionalEventDefinition :errorEventDefinition
                       :escalationEventDefinition :linkEventDefinition
                       :signalEventDefinition :terminateEventDefinition])
(defn- event-definition
  [tnode proot]
  (let [_mp (type-data-definition tnode proot)
        _ev (filter #(some? (some #{(kw-to-refkw (:tag %))} evdefs))
                    (zip/children tnode))]
    (assoc _mp :event-def (mapv #(conj (:attrs %) {:type (:tag %)}) _ev))))

(defn- finish-node
  [node node-type proot]
  (condp = (kw-to-refkw node-type)
    :startEvent       (event-definition node proot)
    :endEvent         (event-definition node proot)

    :task             (task-definition node proot)
    :userTask         (task-definition node proot)
    :scriptTask       (task-definition node proot)
    :sendTask         (task-definition node proot)
    :receiveTask      (task-definition node proot)
    :serviceTask      (task-definition node proot)
    :subProcess       (task-definition node proot)

    :exclusiveGateway (type-data-definition node proot)

    (throw (Exception. (str "Unhandled node-type " node-type)))
    ))

(defn- node-include?
  [node]
  ((complement some?)
   (some (conj #{} (kw-to-refkw (:tag node))) node-ex)))

(defn- nd
  [root tagkw idref]
  (let [_x (zx/xml1-> root tagkw (zx/attr= :id idref))]
    _x))

(defn- nodes-only
  [proot]
  (mapv #(conj (assoc (:attrs %) :type (:tag %) )
               (finish-node (nd proot (:tag %) (:id (:attrs %))) (:tag %) proot)

               #_(finish-node (zx/xml1-> proot (pf (:tag %))
                                     (zx/attr= :id (:id (:attrs %)))) (:tag %) proot)
               )
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
  (let [_se (map :id (tu/nodes-for-type (pf :startEvent) nodes))
        _fe (first (map #(step-def % proot nodes) _se))]
    _fe))

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
  (let [_nodedefs (nodes-only proot)
        _pn (assoc (:attrs (zip/node proot))
              :process-data (data-iospec proot proot)
              :process-object-refs (mapv (comp :attrs zip/node )
                                          (zx/xml-> proot (pf :dataObject)))
              :process-store-refs (mapv (comp :attrs zip/node)
                                    (zx/xml-> proot (pf :dataStoreReference)))
              :process-flows (node-driver proot _nodedefs)
              :process-nodes _nodedefs
              :process-flow-refs (mapv process-lanesets
                                       (zx/xml-> proot (pf :laneSet))))]
    _pn))

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
  (let [_cntx (assoc {} :definition (:attrs (zip/node *zip*)))
        _cntx (assoc-in _cntx [:data-stores]
                        (mapv (comp :attrs zip/node)
                              (zx/xml-> *zip* (pf :dataStore))))
        _cntx (assoc-in _cntx [:processes]
                        (mapv process-def (zx/xml-> *zip* (pf :process))))
        _cntx (assoc-in _cntx [:interfaces]
                        (mapv interface-def (zx/xml-> *zip* (pf :interface))))
        _cntx (assoc-in _cntx [:resources]
                        (mapv #(:attrs (zip/node %))
                              (zx/xml-> *zip* (pf :resource))))
        _cntx (assoc-in _cntx [:messages]
                        (mapv #(:attrs (zip/node %))
                              (zx/xml-> *zip* (pf :message))))
        _cntx (assoc-in _cntx [:items]
                        (mapv items (zx/xml-> *zip* (pf :itemDefinition))))]
    _cntx))

(defn context
  "Takes a parse block and returns process contexts"
  [parse-block]
  (binding [*zip* (:zip parse-block)
            *prefix* (:ns parse-block)]
    (process-context)))


;--------------------------------------------
(comment
  (def _s0 (tu/parse-source (-> "Incident Management.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (tu/parse-source (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (time (def _t0 (context _s0)))
  (use 'clojure.pprint)
  (count (:processes _t0))

  )


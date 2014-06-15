(ns ^{:author "Frank V. Castellucci"
      :doc "tributary BPMN parser functions"}
  tributary.bpmn
  (:require [clojure.zip :as zip]
            [tributary.utils :as tu]
            [clojure.xml :as xml]
            [clojure.set :as set]
            [clojure.data.zip :as dz]
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
  (when node
    (let [_x (clojure.string/split node #":")]
      (condp = (count _x)
        1 (assoc {} :ns nil :ref (_x 0))
        2 (assoc {} :ns (_x 0) :ref (_x 1))
        3 (assoc {} :ns (_x 1) :ref (_x 2))))))

(defn- kw-to-refkw
  "Takes a keyword, parses and returns the leaf"
  [kw]
  (keyword (:ref (dtype (str kw)))))

; data.zip.xml selector extensions

(defn- mtags=
  "Similar to (zx/tag= ...) but takes additional tag keywords to
  'or' in results"
  [coll]
  (fn [loc]
    (filter
     (fn [l] (and (zip/branch? l)
                  (some #(= % (:tag (zip/node l))) coll)))
     (if (dz/auto? loc)
       (dz/children-auto loc)
       (list (dz/auto true loc))))))

(defn- ptags=
  "Annotes keywords with pf prior to calling mtags="
  [& c]
  (mtags= (map pf c)))


;; Data setup

(def ^:private data-attrs [:itemSubjectRef :name :isCollection])
(def ^:private node-ex [:ioSpecification :laneSet :lane :sequenceFlow :dataObject
                        :dataObjectReference :dataStoreReference
                        :textAnnotation :association])

(defn- data-object
  [refdata scope]
  (conj {:reftype (:tag refdata) :scope scope}
        (update-in (:attrs refdata) [:itemSubjectRef] dtype)))

(defn- data-iospec
  "root can be process or node. Takes the result of :ioSpecifiction 'iospec' zip node
  then uses the :itemSubjectRef as :id filter for completing the definition"
  [specroot root]
  (let [_sc (if (= specroot root) :process :task)]
    (mapv #(data-object % _sc)
          (zx/xml-> specroot (pf :ioSpecification)
                    (ptags= :dataInput :dataOutput) zip/node))))

;; Process setup

(defn- type-io
  [node]
  (let [_da   (zx/xml-> node (ptags= :dataInputAssociation :dataOutputAssociation))
        _sv   (mapv #(assoc {} :from (zx/xml1-> % (pf :sourceRef) zx/text)
                       :to (zx/xml1-> % (pf :targetRef) zx/text)) _da)
        _sa   (into [] (flatten (mapv #(map (fn [anode]
                            (assoc {} :to (zx/xml1-> anode :to zx/text)
                                :from (zx/xml1-> anode :from zx/text)))
                            (zx/xml-> % :assignment)) _da)))]
    (assoc {} :bindings _sv :assignments _sa)))

(defn- type-data-definition
  "Returns general map for tasks, events, etc."
  [tnode proot]
  (conj (assoc {}
    :owner-resource []
    :data  (data-iospec tnode proot)) (type-io tnode)))

(def ^:private loop-types [:standardLoopCharacteristics
                           :multiInstanceLoopCharacteristics])

(defn- task-loops
  "Currently just the attributes of the node. BPMN 2.0 declares that conformance
  includes expressions, events, inputs and outputs"
  [tnode]
  (mapv #(conj (:attrs %) {:type (:tag  %)})
        (zx/xml-> tnode (ptags= loop-types) zip/node)))

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

(defn- gbyid
  "Returns the single element of type tagkw with attribute id = idref"
  [root tagkw idref]
  (zx/xml1-> root tagkw (zx/attr= :id idref)))

(defn- nodes-only
  [proot]
  (mapv #(conj (assoc (:attrs %) :type (:tag %) )
               (finish-node (gbyid proot (:tag %) (:id (:attrs %))) (:tag %) proot))
        (filter node-include? (zip/children proot))))

(defn- node-def
  "Generic node definition parse"
  [node-id proot _cat]
  (let [_t (first (filter #(= (:id %) node-id) _cat))
        _n (gbyid proot (:type _t) node-id)
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


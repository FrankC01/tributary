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
      1 (assoc {} :lang nil :ref (_x 0))
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
        _dg  (if (nil? _dg)
               (zx/xml1-> specroot (pf :property) (zx/attr= :itemSubjectRef data-id))
               _dg)]
    (if (nil? _dg)
      {:isCollection false
       :name         data-id
       :id           data-id
       }
    (update-in
     (set/rename-keys
      (conj {:isCollection "false"} (select-keys (:attrs (zip/node _dg)) data-attrs))
      {:itemSubjectRef :id}) [:isCollection] symbol))))

(defn- data-reference
  [refdata reftype]
  (assoc (:attrs (zip/node refdata)) :rtype reftype))

(defn- data-iospec
  "root can be process or task. Takes the result of :ioSpecifiction 'iospec' zip node
  then uses the :itemSubjectRef as :id filter for completing the definition"
  [specroot root]
  (let [_di (map #(data-reference % :input)
                 (zx/xml-> specroot  (pf :ioSpecification) (pf :dataInput)))
        _do (map #(data-reference % :output)
                 (zx/xml-> specroot  (pf :ioSpecification) (pf :dataOutput)))
        ]
    (mapv #(assoc (data-name (:itemSubjectRef %) root specroot)
             :refid (:id %) :reftype (:rtype %))
          (reduce conj _di _do))))

(defn- data-type
  "Takes the current data map, uses ID to search for type information"
  [data-map root typekw]
  (let [_idref (:ref (dtype (:id data-map)))
        _x (select-keys
            (:attrs (zip/node (zx/xml1-> root (pf typekw) (zx/attr= :id _idref))))
             [:structureRef :itemKind])
        _y (assoc {} :kind (_x :itemKind))
        ]
    (conj data-map _y (dtype (:structureRef _x)))))

(defn- global-data
  "Pulls global data definitions"
  [pnode]
  (mapv #(data-type %  *zip* :itemDefinition)
        (data-iospec pnode pnode)))


;; Process setup
; Each lane in the laneset represents a flow

(defn- task-data-binding
  "Returns to and from data bindings in task."
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
    :userTask         (task-definition node proot)
    :scriptTask       (scriptTask-definition node proot)
    :sendTask         nil
    :serviceTask      nil
    :exclusiveGateway nil
    :endEvent         nil
    (throw (Exception. (str "Invalid node-type " node-type)))
    ))

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
                  {:sourceRef :node :targetRef :next})
        _stptype  (select-keys (tu/node-for-id (:node _idsmap) nodes) [:type])
        _conddref (zx/xml1-> sqref (pf :conditionExpression) zx/text)
        _conds    {:predicates
                   [(cond (or (nil? _conddref) (= _conddref "")) :none
                          :else _conddref)]}]
    (conj _idsmap _stptype _conds {:next (step-def (:next _idsmap) proot nodes) })))

(defn- step-def
  "Parse the sequence flow for step definition"
  [srcid proot nodes]
  (let [_sx (zx/xml-> proot (pf :sequenceFlow) (zx/attr= :sourceRef srcid))
        _sd (if (empty? _sx)
              [(conj (select-keys (tu/node-for-id srcid nodes) [:type])
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
  "Parses the nodes and step flows specific to this flow-set"
  [flow-set proot]
  (let [_fl (mapv #(flow-def % proot) (zx/xml-> flow-set (pf :lane)))]
    _fl))

(defn- process-def
  "Parse the process data, nodes and steps"
  [proot]
  (let [_pn (assoc-in (:attrs (zip/node proot)) [:process-data]
                      (global-data proot))
        _pn (assoc-in _pn [:flowsets] (mapv #(flowset-def % proot)
                                           (zx/xml-> proot (pf :laneSet))))
        ]
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

(defn- process-context
  "For each process, parse a process definition context"
  []
  (let [_cntx (assoc {} :processes (mapv #(process-def %)
                                   (zx/xml-> *zip* (pf :process))))
        _cntx (assoc-in _cntx [:interfaces] (mapv interface-def
                                               (zx/xml-> *zip* (pf :interface))))
        _cntx (assoc-in _cntx [:resources] (mapv #(:attrs (zip/node %))
                                               (zx/xml-> *zip* (pf :resource))))
        _cntx (assoc-in _cntx [:messages] (mapv #(:attrs (zip/node %))
                                             (zx/xml-> *zip* (pf :message))))
        ]
    _cntx))

(defn context
  "Takes a parse block and returns process contexts"
  [parse-block]
  (binding [*zip* (:zip parse-block) *prefix* (:ns parse-block)]
    (process-context)))

;--------------------------------------------
(comment
  (use 'clojure.pprint)
  (def _s0 (tu/parse-source "resources/Incident Management.bpmn"))
  (def _t0 (context _s0))


  (pprint (:resources _t0))
  (pprint (:messages _t0))
  (pprint (:interfaces _t0))
  (pprint (:process-data (first (:processes _t0))))
  (keys   (ffirst (:flowsets (first (:processes _t0)))))
  (pprint (first (first (:flowsets (first (:processes _t0))))))
  (pprint (:steps (ffirst (:flowsets (first (:processes _t0))))))
  (pprint (:nodes (ffirst (:flowsets (first (:processes _t0))))))
  (pprint (last (first (:flowsets (first (:processes _t0))))))

  (def _s0 (tu/parse-source "resources/Valid Ticket-LD.bpmn"))
  (def _t0 (context _s0))


  )


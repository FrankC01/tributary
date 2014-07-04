(ns ^{:author "Frank V. Castellucci"
      :doc "tributary BPMN parser functions"}
  tributary.bpmn
  (:require [clojure.zip :as zip]
            [tributary.utils :as tu]
            [tributary.tzip :as tz]
            [clojure.xml :as xml]
            [clojure.set :as set]
            [clojure.data.zip.xml :as zx])
  )

;; Bindings
(def ^:dynamic ^:private *nodes* nil)

;; node parsing functions
(def data-btype {:dataObject :object
                 :dataInput  :input
                 :dataOutput :output })

(defn- association
  [loc]
  (let [_tag (tu/nskw2kw (:tag (zip/node loc)))
        _srcref (zx/xml1-> loc (tu/pref :sourceRef) zip/node)
        _targref (zx/xml1-> loc (tu/pref :targetRef) zip/node)
        _assigns (zx/xml-> loc :assignment zip/node)]
    (conj
     {:tag _tag
      :attrs (conj {:dtype :association}
                   {:source (:content _srcref)}
                   {:target (:content _targref)})}
     (if (empty? _assigns)
       {:content nil}
       {:content [{:tag :assignments
                   :attrs nil
                   :content [_assigns]}]}
       ))))


(defn- item-aware-block
  "Returns a map for node, simplifying reference information and
  extending to include :data-grp derived from input/output associations"
  [loc & [tag base]]
  (let [_node  (zip/node loc)
        _keyw  (:ref (tu/nsref (str (:tag _node))))
        _typew (tu/pref (keyword (str _keyw "Association")))
        _base  (tu/tagblock _node  tag)
        _assoc (mapv association (zx/xml-> (or base (zip/up loc)) _typew))
        _aflag (if (empty? _assoc) nil )]
    (assoc _base
      :attrs (assoc (update-in (set/rename-keys (:attrs _base)
                                                {:itemSubjectRef :item-ref})
                               [:item-ref]  (comp :ref tu/nsref))
               :dtype ((keyword _keyw) data-btype))
      :content (if (empty? _assoc) nil _assoc)
      )))

(defn- iocontrol-aware-block
  "Returns a map for node, simplifying reference information and
  extending to include :data-grp derived from input/output associations"
  [loc & [tag]]
  (item-aware-block loc tag (zip/up (zip/up loc))))


(def ^:private evdefs [:messageEventDefinition :timerEventDefinition
                       :cancelEventDefinition :compensateEventDefinition
                       :conditionalEventDefinition :errorEventDefinition
                       :escalationEventDefinition :linkEventDefinition
                       :signalEventDefinition :terminateEventDefinition])

; adds (additional information handling) tuple identify additional semantics of
; a node that should be captured in base nodes content such that each add tuple
; [0 keyword
;  1 nil or individual item handler (nil is default and uses the tu/tagblock function)
;  2 zx/xml-> selector extensions]
;
; grps (groups) tuples identify grouping of addition information on the node
; in focus such that the group tuple that is dispatched vis-a-vis
; (% loc)
; e.g.
; {:dtype :foo
;   :adds nil
;   :grps [#(nodegroup % [:data-group
;                       nil
;                       #(item-aware-block % :data)
;                       (tu/pref :dataOutput)])
;          #(...)]
;          }


(declare process-node-grp nodegroup)

(def ^:private event
  {:dtype :event
   :adds nil
   :grps [#(nodegroup % [:data
                       nil
                       (fn [loc] (item-aware-block loc :data))
                       (tu/pref :dataOutput)])
          #(nodegroup % [:definition
                      nil
                      (comp tu/tagblockwc zip/node)
                      (apply tu/ptags= evdefs)])]
          })

(def ^:private gateway
  {:dtype :gateway
   :adds nil
   :grps nil})

(def ^:private activity
  {:dtype :activity
   :adds [[:loop #(tu/tagblockmap % {:dtype :loop})
           (tu/ptags= :multiInstanceLoopCharacteristics
                   :standardLoopCharacteristics) zip/node]]
   :grps [#(nodegroup %
           [:data
            nil
            (fn [loc] (iocontrol-aware-block loc :data))
           (tu/pref :ioSpecification)  (tu/ptags= :dataInput :dataOutput)])
           ]})

(def ^:private script
  (update-in activity [:adds] conj
             [:script #(tu/tagblockwc % :script) (tu/pref :script) zip/node]))

(def ^:private usertask
  (update-in activity [:adds] conj
             [:owner #(tu/tagblockwc % :owner)
              (tu/pref :potentialOwner) (tu/pref :resourceRef) zip/node]))

(def ^:private subprocess
  {:dtype :subprocess
   :adds nil
   :grps [
          #(nodegroup % [:data
                       nil
                       (fn [loc] (iocontrol-aware-block loc :data))
                       (tu/pref :ioSpecification)  (tu/ptags= :dataInput :dataOutput)])
          #(process-node-grp %)
          ]})

(def ^:private
  supported {:startEvent                 event
             :boundaryEvent              event
             :intermediateCatchEvent     event
             :endEvent                   event
             :intermediateThrowEvent     event
             :implicitThrowEvent         event

             :task                       activity
             :userTask                   usertask
             :scriptTask                 script
             :sendTask                   activity
             :receiveTask                activity
             :serviceTask                activity
             :callActivity               activity

             :subProcess                 subprocess

             :exclusiveGateway           gateway
             :parallelGateway            gateway
             :inclusiveGateway           gateway
             :eventBasedGateway          gateway
             })


(defn nodegroup
  [loc & [[grpkw grouper handler & sel]]]
  ((or grouper tu/group-definition) (apply zx/xml-> loc sel) grpkw handler))

(defn nodeadd
  [loc & [[nodekw node-handler & sel]]]
  (mapv (or node-handler #(tu/tagblock (zip/node %) nodekw)) (apply zx/xml-> loc sel)))

(defn nodeblock
  "Parse and emit node information and associated groups.
  The pbloc is derived directly from supported lookups or alength
  function association"
  [loc]
  (let [_def   (zip/node loc)
        _tag   (tu/nskw2kw (:tag _def))
        _pbloc (_tag supported)]
    (assoc (assoc-in _def [:attrs :dtype] (:dtype _pbloc))
      :tag     _tag
      :content (or (vec (flatten (conj (mapv #(nodeadd loc %) (:adds _pbloc))
                                       (mapv #(% loc) (:grps _pbloc))
                                       ))) nil))))

;; sequenceFlow parsing

(def ^:dynamic ^:private *cycle* nil)

(defn- is-visited?
  [node-id]
  (some? (some #{node-id} @*cycle*)))

(declare flow-step)

(defn- make-sequence
  [node & [content flowseq constraints]]
  {:tag :sequence
   :attrs {:dtype :execute
           :seq-id (:id (:attrs flowseq)) :seq-name (:name (:attrs flowseq))
           :predicates [constraints]
           :node-id (:id (:attrs node )) :node-tag (:tag node)
           }
   :content content}
  )

(defn- flow-helper
  [seqrf proot]
  (into [] (flatten (map #(flow-step % proot)
                         (conj (zx/xml-> *nodes* :boundaryEvent
                                         (zx/attr= :attachedToRef (zx/attr seqrf :sourceRef))
                                         (zx/attr :id))
                               (zx/attr seqrf :targetRef))))))

(defn- flow-noderef
  [seqf proot node]
  (let [_conddref (zx/xml1-> seqf (tu/pref :conditionExpression) zx/text)]
    (make-sequence node
                   (flow-helper seqf proot)
                   (zip/node seqf)
                   (cond (or (nil? _conddref) (= _conddref "")) :none
                                :else _conddref))))

(defn- flow-step
  [srcid proot]
  (let [_seqf (zx/xml-> proot (tu/pref :sequenceFlow) (zx/attr= :sourceRef srcid))
        _node (zx/xml1-> *nodes* (tz/mtags= (keys supported))
                         (zx/attr= :id srcid) zip/node)]
    (cond
     (= (:tag _node) :endEvent) (assoc-in (make-sequence _node) [:attrs :dtype] :end)
     (is-visited? srcid) (assoc-in (make-sequence _node) [:attrs :dtype] :jump)
     :else (do
             (swap! *cycle* conj srcid)
             (mapv #(flow-noderef % proot _node) _seqf)
             )
     )))

; TODO: In lane attributes, the partitionElementRef may be resource ref
; need to either rename or ignore/drop from returns

(defn- process-flow
  "Parses the sequence flow from source and structures
  the execution path tree"
  [proot]
  (binding [*cycle* (atom [])]
    (let [_start (zx/xml-> *nodes* :startEvent (zx/attr :id))]
      (reset! *cycle* [])
      {:tag :group
                  :attrs {:count (count _start) :dtype :sequence}
                  :content (vec (flatten (reduce conj (mapv #(flow-step % proot) _start))))
                  }))
  )

(defn- process-data-grp
  "Combines the iocontrol-aware-block and item-aware-block information for
  process only, all other are defined in node handling"
  [loc & tail]
  (let [_ioc  (mapv (comp tu/tagblock zip/node)
                    (zx/xml-> loc (tu/pref :ioSpecification) (tu/ptags= :dataInput :dataOutput)))
        _data (mapv #(item-aware-block % :data) (zx/xml-> loc (tu/pref :dataObject)))
        _cnt (reduce conj _data _ioc)]
    {:tag :group
     :attrs {:count (count _cnt) :dtype :data}
     :content _cnt})
  )

(defn- process-node-grp
  [proot & tail]
  (tu/group-definition (zx/xml-> proot (apply tu/ptags= (keys supported)))
                                 :node nodeblock))

(defn- process-def
  "Parse the process data, nodes and steps"
  [proot]
  (binding [*nodes* (zip/xml-zip (process-node-grp proot))]
    (assoc (tu/tagblock (zip/node proot)) :content
              [
               (process-data-grp proot)
               (tu/group-definition (zx/xml-> proot (tu/pref :resource)) :resource
                     (comp tu/tagblock zip/node))

               (tu/group-definition  (zx/xml-> proot (tu/pref :dataStoreReference))
                               :store (comp tu/tagblock zip/node))
               (process-flow proot)
               (zip/node *nodes*)
               ])))

(defn- operations
  [ifacez ifaceref]
  (let [_ops (mapv #(assoc (tu/tagblock %) :content (:content %))
                   (zx/xml-> ifacez (tu/pref :operation) zip/node))]
    (assoc-in ifaceref [:content] _ops)))

; TODO: Import statement handling (content of itemDefinition)

(defn- process-context
  "Parses the definition context and then recurses down
  to each process and subprocess definition"
  []
  [(tu/group-definition (zx/xml-> tu/*zip* (tu/pref :message)) :message
                     (comp tu/tagblock zip/node))
   (tu/group-definition (zx/xml-> tu/*zip* (tu/pref :resource)) :resource
                     (comp tu/tagblock zip/node))
   (tu/group-definition (zx/xml-> tu/*zip* (tu/pref :dataStore)) :store
                     #(tu/tagblock (zip/node %) :store))
   (tu/group-definition (zx/xml-> tu/*zip* (tu/pref :itemDefinition)) :item
                     #(tu/tagblock (zip/node %) :item))
   (tu/group-definition (zx/xml-> tu/*zip* (tu/pref :interface)) :interface
                     #(operations % (tu/tagblock (zip/node %))))
   (tu/group-definition (zx/xml-> tu/*zip* (tu/pref :process)) :process process-def)])

(defn context
  "Takes a parse block and returns process context, wrapping all elements in
  the {:tag :attrs :content}
  format"
  [parse-block]
  (binding [tu/*zip* (:zip parse-block)
            tu/*prefix* (:ns parse-block)]
    (assoc {:tag :context
            :attrs (:attrs (zip/node tu/*zip*))
           } :content (process-context))))

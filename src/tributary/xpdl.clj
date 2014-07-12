(ns ^{:author "Frank V. Castellucci"
      :doc "tributary XPDL parser functions"}
  tributary.xpdl
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.set :as set]
            [tributary.utils :as tu]
            [tributary.tzip :as tz]
            [clojure.data.zip.xml :as zx])
  )

;; Bindings
(def ^:dynamic ^:private *nodes* nil)

;; Forward declarations
(declare process-def)

(def ^:private
  tag-map {
           :EndEvent   :endEvent
           :StartEvent :startEvent
           :Task       :task
           :Tool       :call
           :SubFlow    :subProcess
           })

(defn- with-data
  [loc tag]
  (let [_base (tu/tagblock (zip/node loc) tag)
        _init {:value (zx/xml1-> loc (tu/pref :InitialValue) zx/text)}
        _type (zip/down (zip/down loc))
        _prec (if (empty? (zip/down _type))
                nil
                (first (:content (zip/node (zip/down _type)))))]
    (update-in _base [:attrs]
               merge
               _init
               {:dtype (:Type (:attrs (zip/node _type)))
                :precision _prec
                })))

(defn- transition
  [loc]
  (assoc (update-in  (tu/tagblock (zip/node (zip/down loc))
               ((comp tu/kwtolckw tu/nskw2kw :tag zip/node zip/down) loc))
                     [:attrs] set/rename-keys {:Type :dtype})
    :content (mapv (comp :Id :attrs)
              (zx/xml-> loc zip/down (tu/pref :TransitionRefs) zip/children))
  ))

(defn- activity
  [bloc tag & [atmap]]
  (assoc (tu/tagblockmap (zip/node bloc) (or atmap {:dtype :activity})
                         (or tag :activity))
    :content
    [(tu/group-definition
      (zx/xml-> bloc (tu/pref :TransitionRestrictions)
                (tu/pref :TransitionRestriction))
      :transition transition)]))


(defn- route
  [bloc rloc]
  (let [_gtype (:GatewayType (:attrs (zip/node rloc)))]
    (activity bloc
              (cond
               (= _gtype "XOR")       :exclusiveGateway
               (= _gtype "Exclusive") :exclusiveGateway
               (= _gtype "OR")        :inclusiveGateway
               (= _gtype "Inclusive") :inclusiveGateway
               (= _gtype "AND")       :parallelGateway
               (= _gtype "Parallel")  :parallelGateway
               (= _gtype "Complex")   :complexGateway
               ) {:dtype :gateway})))

(defn- event
  [bloc eloc]
  (activity bloc (-> eloc zip/down zip/node :tag tu/nskw2kw tag-map)
            {:dtype :event}))

(defn- task
  "Fundemental task parse"
  [bloc]
  (update-in
   (activity bloc :task)
   [:content] #(vec (flatten (conj % (map (fn [rec] (tu/tagblockwc rec :owner))
                         (zx/xml-> bloc (tu/pref :Performers) (tu/pref :Performer)
                                   zip/node)))))))

(defn- actualsbloc
  "Parses actual parameters into content block"
  [loc]
  (tu/map2lckws (assoc (tu/tagblock (zip/node loc) :invoke)
                  :content (mapv
                            (comp first :content)
                            (zx/xml-> loc (tu/pref :ActualParameters)
                                      (tu/pref :ActualParameter) zip/node)))))

(defn- subflow
  [bloc iloc]
  (update-in (activity bloc :subflow)
             [:attrs] merge
             (set/rename-keys (:attrs (zip/node (zip/down iloc))) {:Id :pid})))

(defn- tool
  [loc]
  (update-in (actualsbloc loc)
             [:attrs] set/rename-keys {:type :dtype :id :appid}))


(defn- modemap
  [kw loc]
  {kw (if (empty? loc)
        nil
   ((comp tu/nskw2kw :tag zip/node zip/down) loc))})

(defn- call
  "Parses activity application 'calls'"
  [bloc iloc]
  (let [_calls (tu/group-definition (zx/xml-> iloc (tu/pref :Task)
                                              (tu/pref :TaskApplication))
                                    :invoke
                                    tool)
        _base  (activity bloc :callActivity {:dtype :call}) ]
    (update-in _base [:content] conj _calls)))

(defn- implementation
  [bloc iloc]
  (let [_kw (-> iloc zip/down zip/node :tag tu/nskw2kw)]
    (cond
     (and (= _kw :Task) (= (-> iloc zip/down zip/down zip/node :tag tu/nskw2kw)
                           :TaskApplication)) (call bloc iloc)
     (= _kw :SubFlow) (subflow bloc iloc)
     :else (task bloc)
     )))

(defn- blockactivity
  "Similar to BPMN subProcess than SubFlow"
  [bloc iloc]
  (let [_base (update-in (activity bloc :subprocess) [:attrs]
             assoc  :encapsulated "true")]
    (assoc _base :content []))
  )

(def ^:private
  trans {:Route            route
         :Event            event
         :Implementation   implementation
         :BlockActivity    blockactivity
         })

(defn- node-type
  [loc]
  (let [_base (zx/xml1-> loc (tu/ptags= :Route :Event :Implementation :BlockActivity))
        _btype (tu/nskw2kw (:tag (zip/node _base)))]
    (update-in ((_btype trans) loc _base) [:attrs] tu/map2lckws)))


(defn- process-node-grp
  [proot & tail]
  (tu/group-definition (zx/xml-> proot (tu/pref :Activities) (tu/pref :Activity))
                       :node node-type))

;; Flow sequencing

(def ^:dynamic ^:private *cycle* nil)

(defn- is-visited?
  [node-id]
  (some? (some #{node-id} @*cycle*)))

(defn- make-sequence
  [node & [content flowseq constraints]]
  {:tag :sequence
   :attrs {:dtype :execute
           :seq-id (:Id (:attrs flowseq)) :seq-name (:Name (:attrs flowseq))
           :predicates [(or constraints :none)]
           :node-id (:id (:attrs node )) :node-tag (:tag node)
           }
   :content content}
  )

(declare flow-step)

(defn- flow-helper
  [seqrf proot]
  (into [] (flatten (map #(flow-step % proot)
                         (conj (zx/xml-> *nodes* :boundaryEvent
                                         (zx/attr= :attachedToRef (zx/attr seqrf :From))
                                         (zx/attr :id))
                               (zx/attr seqrf :To))))))

(defn- flow-noderef
  [seqf proot node]
  (let [_conddref (zx/xml1-> seqf (tu/pref :Condition) zx/text)]
    (make-sequence node
                   (flow-helper seqf proot)
                   (zip/node seqf)
                   (cond (or (nil? _conddref) (= _conddref "")) :none
                                :else _conddref))))

(defn- flow-step
  [srcid proot]
  (let [_seqf (zx/xml-> proot (tu/pref :Transition) (zx/attr= :From srcid))
        _node (zx/xml1->
               *nodes*
               (tz/mtags= [:startEvent :endEvent :exclusiveGateway :execute :task :callActivity])
               (zx/attr= :id srcid) zip/node)]
    (cond
     (= (:tag _node) :endEvent) (assoc-in (make-sequence _node) [:attrs :dtype] :end)
     (is-visited? srcid) (assoc-in (make-sequence _node) [:attrs :dtype] :jump)
     :else (do
             (swap! *cycle* conj srcid)
             (mapv #(flow-noderef % proot _node) _seqf)
             )
     )))

(defn- process-flow
  "Parses the sequence flow from source and structures
  the execution path tree"
  [proot]
  (binding [*cycle* (atom [])]
    (let [_start (zx/xml-> *nodes* :startEvent (zx/attr :id))
          _troot (zx/xml1-> proot (tu/pref :Transitions))]
      (reset! *cycle* [])
      {:tag :group
                  :attrs {:count (count _start) :dtype :sequence}
                  :content (vec (flatten (reduce conj (mapv #(flow-step % _troot) _start))))
                  }))
  )

;; Resource

(defn- resource
  "Parse a participant as a :resource, including it's type"
  [loc]
  (update-in (tu/tagblock (zip/node loc) :resource)
   [:attrs] assoc :resource-type
             (zx/xml1-> loc (tu/pref :ParticipantType) (zx/attr :Type))))

(defn- resources
  [proot]
  (tu/group-definition
   (zx/xml-> proot (tu/pref :Participants) (tu/pref :Participant))
   :resource
   (comp tu/map2lckws resource)))


(defn- parameters
  "Returns a data group based on FormalParameters"
  [loc]
  (tu/group-definition (zx/xml-> loc (tu/pref :FormalParameters)
                                    (tu/pref :FormalParameter))
                          :data
                          (comp tu/map2lckws
                                #(with-data % :data))))

;; Process

(defn- process-def
  "WorkflowProcess and ActivitySet parse. ActivitySet is considered a :subProcess of
  the WorkflowProcess declaring it."
  [proot & [tag]]
  (binding [*nodes* (zip/xml-zip (process-node-grp proot))]
    (assoc (tu/map2lckws (tu/tagblock (zip/node proot) (or tag :process)))
      :content
      [
       (resources proot)
       (tu/group-definition
        (zx/xml-> proot (tu/pref :ActivitySets) (tu/pref :ActivitySet))
        :subprocess
        #(process-def % :subprocess))
       (tu/group-definition
        (zx/xml-> proot (tu/ptags= :DataFields :FormalParameters)
                  (tu/ptags= :DataField :FormalParameter))
        :data (comp tu/map2lckws #(with-data % :data)))
       ;(process-data-grp proot)
       ;(tu/group-definition  (zx/xml-> proot (tu/pref :dataStoreReference))
       ;                      :store (comp tu/tagblock zip/node))
       #_(process-flow proot)
       (zip/node *nodes*)
       ])))


(defn- interface
  "Parses paramaters and attributes for interface (Application) loc"
  [loc]
  (assoc (tu/map2lckws (tu/tagblock (zip/node loc) :interface))
    :content
    [(parameters loc)
     (tu/group-definition (zx/xml-> loc (tu/pref :ExtendedAttributes)
                                    (tu/pref :ExtendedAttribute))
                          :attribute
                          (comp tu/map2lckws
                                #(tu/tagblock % :attribute) zip/node))]
    ))

(defn- process-context
  "Parse and populate XPDL context and processes."
  []
  [(resources tu/*zip*)
   (tu/group-definition
    (zx/xml-> tu/*zip* (tu/pref :Applications) (tu/pref :Application))
    :interface interface)
   (tu/group-definition
    (zx/xml-> tu/*zip* (tu/pref :MessageFlows) (tu/pref :MessageFlow))
    :message (comp tu/map2lckws #(tu/tagblock % :message) zip/node))
   (tu/group-definition
    (zx/xml-> tu/*zip* (tu/pref :Stores) (tu/pref :Store))
    :store (comp tu/map2lckws tu/tagblock zip/node))
   (tu/group-definition
    (zx/xml-> tu/*zip* (tu/pref :DataFields) (tu/pref :DataField))
    :item
    (comp tu/map2lckws #(with-data % :item)))
   (tu/group-definition
    (zx/xml-> tu/*zip* (tu/pref :WorkflowProcesses) (tu/pref :WorkflowProcess))
    :process process-def)
   ])


(defn- context-parse
  "Returns the attribute and PackageHeader information as
  a map of associative name values"
  []
  (let [_script {:script (:Type (:attrs (zx/xml1-> tu/*zip* (tu/pref :Script) zip/node)))}]
    (conj (apply merge
               (map (comp #(assoc {} (:tag %) (first (:content %)))
                          tu/tagblockwc)
                    (zx/xml-> tu/*zip*
                              (tu/ptags= :PackageHeader :RedefinableHeader)
                              zip/children)))
          _script
        (:attrs (zip/node tu/*zip*))))
  )

(defn context
  "Takes a parse block and returns process context, wrapping all elements in
  the {:tag :attrs :content}
  format"
  [parse-block]
  (binding [tu/*zip* (:zip parse-block)
            tu/*prefix* (:ns parse-block)]
    (assoc {:tag :context
            :attrs (tu/map2lckws (context-parse))
           } :content (process-context))))

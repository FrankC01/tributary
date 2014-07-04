(ns ^{:author "Frank V. Castellucci"
      :doc "tributary XPDL parser functions"}
  tributary.xpdl
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [tributary.utils :as tu]
            [clojure.data.zip.xml :as zx])
  )

;; Bindings
(def ^:dynamic ^:private *nodes* nil)


(def ^:private
  tag-map {
             :EndEvent   :endEvent
             :StartEvent :startEvent
             :Task       :task
             })

(defn- route
  [bloc rloc]
  (tu/tagblockmap (zip/node bloc) {:dtype :gateway}
               (condp (:GatewayType (:attrs (zip/node rloc)))
                     "XOR" :exclusiveGateway)))

(defn- event
  [bloc eloc]
  (tu/tagblockmap (zip/node bloc) {:dtype :event}
               (-> eloc zip/down zip/node :tag tu/nskw2kw tag-map)))

(defn- implementation
  [bloc iloc]
  (assoc (tu/tagblockmap (zip/node bloc) {:dtype :activity}
               (-> iloc zip/down zip/node :tag tu/nskw2kw tag-map))
             :content (mapv #(tu/tagblockwc % :owner)
                         (zx/xml-> bloc (tu/pref :Performers) (tu/pref :Performer)
                                   zip/node))))

(def ^:private
  trans {:Route            route
         :Event            event
         :Implementation   implementation
         })

(defn- node-type
[loc]
  (let [_base ((comp tu/nskw2kw :tag zip/node zip/down) loc)]
    (update-in ((_base trans) loc (zip/down loc))
               [:attrs] tu/map2lckws)))

(defn- data-type
  [loc]
  (let [_base (tu/tagblock (zip/node loc) :data)
        _type (zip/down (zip/down loc))
        _prec (zip/down _type)
        ]
    (update-in _base [:attrs]
               merge
               {:type (:Type (:attrs (zip/node _type)))
                :precision (first (:content (zip/node _prec)))
                })))

(defn- process-node-grp
  [proot & tail]
  (tu/group-definition (zx/xml-> proot (tu/pref :Activities) (tu/pref :Activity))
                       :node node-type))

(defn- resources
  [proot]
  (tu/group-definition (zx/xml-> proot
                                 (tu/pref :Participants)
                                 (tu/pref :Participant))
                       :resource
                       (comp tu/map2lckws #(tu/tagblock % :resource) zip/node)))

(defn- process-def
  [proot]
  (binding [*nodes* (zip/xml-zip (process-node-grp proot))
            ]
    (assoc (tu/map2lckws (tu/tagblock (zip/node proot)  :process))
      :content
      [
       (resources proot)
       (tu/group-definition (zx/xml-> proot (tu/pref :DataFields)
                                      (tu/pref :DataField))
                            :data
                            (comp tu/map2lckws data-type))
       ;(process-data-grp proot)
       ;(tu/group-definition  (zx/xml-> proot (tu/pref :dataStoreReference))
       ;                      :store (comp tu/tagblock zip/node))
       ;(process-flow proot)
       (zip/node *nodes*)
       ])))

(defn- process-context
  "Parse and populate XPDL context and processes."
  []
  ; messages
  ; stores
  ; items
  ; interfaces
   [(resources tu/*zip*)
    (tu/group-definition (zx/xml-> tu/*zip*
                                   (tu/pref :WorkflowProcesses)
                                   (tu/pref :WorkflowProcess))
                         :process process-def)
    ])


(defn- context-parse
  "Returns the attribute and PackageHeader information as
  a map of associative name values"
  []
  (conj (apply merge
               (map (comp #(assoc {} (:tag %) (first (:content %)))
                          tu/tagblockwc)
                    (zx/xml-> tu/*zip*
                              (tu/ptags= :PackageHeader :RedefinableHeader)
                              zip/children)))
        (:attrs (zip/node tu/*zip*))))

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

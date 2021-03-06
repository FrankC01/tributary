(ns ^{:author "Frank V. Castelluci"
      :doc "Development only spikes"}
  tributary.user
  (:require [tributary.core :refer :all]
            [tributary.tzip :as tz]
            [tributary.utils :as tu]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.pprint :refer :all]
   ))


;;   (time (def xpdl (context-from-source (-> "Sample Messages.xpdl"
;;                clojure.java.io/resource
;;                clojure.java.io/file))))


;;   (def xzip (zip/xml-zip xpdl))
;;   (time (tz/pretty-summary xzip :ppred #(contains? #{:process :context}
;;                                         (:tag (zip/node %)))))

;--------------------------------------------
(comment

  ;; Verified BPMN sources
  (def bpmn (context-from-source (-> "Incident Management.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (context-from-source (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (context-from-source (-> "Hardware Retailer.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (context-from-source (-> "Order Fulfillment.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (context-from-source (-> "Travel Booking.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))
  (def _s0 (context-from-source (-> "Email Voting.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  ; setup zipper
  (def bzip (zip/xml-zip bpmn))

  ; general data.zip.xml selector stuff for BPMN

  (pprint (zx/xml-> bzip :group (zx/attr= :dtype :message) zip/node))
  (pprint (zx/xml-> bzip :group (zx/attr= :dtype :item) zip/node))
  (pprint (zx/xml-> bzip :group (zx/attr= :dtype :store) zip/node))
  (pprint (zx/xml-> bzip :group (zx/attr= :dtype :resource) zip/node))
  (pprint (zx/xml-> bzip :group (zx/attr= :dtype :interface) zip/node))
  (pprint (zx/xml-> bzip :group (zx/attr= :dtype :process) zip/node))

  ; groups selectors in play

  (pprint (count (zx/xml-> bzip tz/groups :message)))
  (pprint (count (zx/xml-> bzip tz/groups :item)))
  (pprint (count (zx/xml-> bzip tz/groups :store)))
  (pprint (count (zx/xml-> bzip tz/groups :resource)))
  (pprint (count (zx/xml-> bzip tz/groups :interface)))
  (pprint (count (zx/xml-> bzip tz/groups :process)))

  ; cattr= selectors in play

  (pprint (zx/xml-> bzip tz/groups :process zip/node))
  (pprint (zx/xml-> bzip tz/groups :process (tz/cattr= :dtype :subprocess) zip/node))
  (pprint (zx/xml-> bzip tz/groups :process (tz/cattr= :dtype :resource) zip/node))
  (pprint (zx/xml-> bzip tz/groups :process (tz/cattr= :dtype :data) zip/node))
  (pprint (zx/xml-> bzip tz/groups :process (tz/cattr= :dtype :store) zip/node))
  (pprint (zx/xml-> bzip tz/groups :process (tz/cattr= :dtype :node) zip/node))
  (pprint (zx/xml-> bzip tz/groups :process (tz/cattr= :dtype :sequence) zip/node))

  ; Process activity nodes
  (pprint (zx/xml-> bzip tz/groups
                     :process
                     (tz/cattr= :dtype :node)
                    tz/children
                    zip/node))

  ; pretty print group summary information

  (time (tz/pretty-summary bzip))
  (time (tz/pretty-summary bzip :ppred #(contains? #{:process :context}
                                        (:tag (zip/node %)))))

  ; Same as above but more realistic for manipulation

  (pprint (zx/xml-> bzip :group (comp #(select-keys % [:dtype :count]) :attrs zip/node)))
  (pprint (take 4 (zx/xml-> bzip tz/groups :process :group
                            (comp #(select-keys % [:dtype :count]) :attrs zip/node) )))

  ;; Verified XPDL sources

  (def xpdl (context-from-source (-> "Simple Lanes.xpdl"
               clojure.java.io/resource
               clojure.java.io/file)))

  (time (def xpdl (context-from-source (-> "HealthCare WF.xpdl"
               clojure.java.io/resource
               clojure.java.io/file))))

  (time (def xpdl (context-from-source (-> "Sample Messages.xpdl"
               clojure.java.io/resource
               clojure.java.io/file))))

  ; setup zipper
  (def xzip (zip/xml-zip xpdl))

  ; general data.zip.xml selector stuff for XPDL

  (pprint (zx/xml-> xzip :group (zx/attr= :dtype :message) zip/node))
  (pprint (zx/xml-> xzip :group (zx/attr= :dtype :item) zip/node))
  (pprint (zx/xml-> xzip :group (zx/attr= :dtype :store) zip/node))
  (pprint (zx/xml-> xzip :group (zx/attr= :dtype :resource) zip/node))
  (pprint (zx/xml-> xzip :group (zx/attr= :dtype :interface) zip/node))
  (pprint (zx/xml-> xzip :group (zx/attr= :dtype :process) zip/node))

  ; groups selectors in play

  (pprint (count (zx/xml-> xzip tz/groups :message)))
  (pprint (count (zx/xml-> xzip tz/groups :item)))
  (pprint (count (zx/xml-> xzip tz/groups :store)))
  (pprint (count (zx/xml-> xzip tz/groups :resource)))
  (pprint (count (zx/xml-> xzip tz/groups :interface)))
  (pprint (count (zx/xml-> xzip tz/groups :process)))

  ; cattr= selectors in play

  (pprint (zx/xml-> xzip tz/groups :process zip/node))
  (pprint (zx/xml-> xzip tz/groups :process (tz/cattr= :dtype :subprocess) zip/node))
  (pprint (zx/xml-> xzip tz/groups :process (tz/cattr= :dtype :resource) zip/node))
  (pprint (zx/xml-> xzip tz/groups :process (tz/cattr= :dtype :data) zip/node))
  (pprint (zx/xml-> xzip tz/groups :process (tz/cattr= :dtype :store) zip/node))
  (pprint (zx/xml-> xzip tz/groups :process (tz/cattr= :dtype :node) zip/node))
  (pprint (zx/xml-> xzip tz/groups :process (tz/cattr= :dtype :sequence) zip/node))

  ; Process activity nodes (XPDL)
  (pprint (zx/xml-> xzip tz/groups
                     :process
                     (tz/cattr= :dtype :node)
                    tz/children
                    zip/node))

  ; pretty print group summary information

  (time (tz/pretty-summary xzip))
  (time (tz/pretty-summary xzip :ppred #(contains? #{:process :context}
                                        (:tag (zip/node %)))))

  ; Same as above but more realistic for manipulation

  (pprint (zx/xml-> xzip :group (comp #(select-keys % [:dtype :count]) :attrs zip/node)))
  (pprint (take 4 (zx/xml-> xzip tz/groups :process :group
                            (comp #(select-keys % [:dtype :count]) :attrs zip/node) )))
  )



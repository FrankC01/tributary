(ns ^{:author "Frank V. Castelluci"
      :doc "Development only spikes"}
  tributary.user
  (:require [tributary.core :refer :all]
            [tributary.tzip :as tz]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.pprint :refer :all]
   ))


  (def _s0 (context-from-source (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (def _z0 (zip/xml-zip _s0))


;--------------------------------------------
(comment

  ; Verified sources

  (def _s0 (context-from-source (-> "Incident Management.bpmn"
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

  (def _z0 (zip/xml-zip _s0))

  ; general data.zip.xml selector stuff

  (pprint (zx/xml-> _z0 :group (zx/attr= :dtype :message) zip/node))
  (pprint (zx/xml-> _z0 :group (zx/attr= :dtype :item) zip/node))
  (pprint (zx/xml-> _z0 :group (zx/attr= :dtype :store) zip/node))
  (pprint (zx/xml-> _z0 :group (zx/attr= :dtype :resource) zip/node))
  (pprint (zx/xml-> _z0 :group (zx/attr= :dtype :interface) zip/node))
  (pprint (zx/xml-> _z0 :group (zx/attr= :dtype :process) zip/node))

  ; groups selectors in play

  (pprint (count (zx/xml-> _z0 tz/groups :message)))
  (pprint (count (zx/xml-> _z0 tz/groups :item)))
  (pprint (count (zx/xml-> _z0 tz/groups :store)))
  (pprint (count (zx/xml-> _z0 tz/groups :resource)))
  (pprint (count (zx/xml-> _z0 tz/groups :interface)))
  (pprint (count (zx/xml-> _z0 tz/groups :process)))

  ; cattr= selectors in play

  (pprint (zx/xml-> _z0 tz/groups :process zip/node))
  (pprint (zx/xml-> _z0 tz/groups :process (tz/cattr= :dtype :data) zip/node))
  (pprint (zx/xml-> _z0 tz/groups :process (tz/cattr= :dtype :store) zip/node))
  (pprint (zx/xml-> _z0 tz/groups :process (tz/cattr= :dtype :node) zip/node))
  (pprint (zx/xml-> _z0 tz/groups :process (tz/cattr= :dtype :sequence) zip/node))

  ; pretty print group summary information

  (time (tz/pretty-summary _z0))
  (time (tz/pretty-summary _z0 :ppred #(contains? #{:process :context}
                                        (:tag (zip/node %)))))

  ; Same as above but more realistic for manipulation

  (pprint (zx/xml-> _z0 :group (comp #(select-keys % [:dtype :count]) :attrs zip/node)))
  (pprint (take 4 (zx/xml-> _z0 tz/groups :process :group
                            (comp #(select-keys % [:dtype :count]) :attrs zip/node) )))
  )



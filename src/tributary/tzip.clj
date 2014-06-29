(ns ^{:author "Frank V. Castelluci"
      :doc "Tributary Zipper"}
  tributary.tzip
  (:require [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.data.zip :as dz]
            [clojure.pprint :refer :all]
   ))

; data.zip.xml ready query predicates and filters

(defn mtags=
  "Similar to clojure.data.zip.xml (tag= ...) but returns a query predicate
  that matches any node in the coll"
  [coll]
  (fn [loc]
    (filter
     (fn [l] (and (zip/branch? l)
                  (some #(= % (:tag (zip/node l))) coll)))
     (if (dz/auto? loc)
       (dz/children-auto loc)
       (list (dz/auto true loc))))))

(defn cattr
  "Returns a filter function of attribute values by key attrkw from
  each child of loc"
  [attrkw]
  (fn [loc]
    (map (comp attrkw :attrs zip/node) (dz/children loc))))

(defn cattr=
  "Returns a query predicate that filters a sequence of immediate children
  locs where the key attrkw value matches attrval."
  [attrkw attrval]
  (fn [loc]
    (filter #(= (attrkw (:attrs (zip/node %))) attrval) (dz/children loc))))

(defn children
  "Returns a sequence of immediate children loc"
  [loc]
  (dz/children loc))

(defn groups
  [loc]
  "Returns a sequence of group loc from current loc"
  (zx/xml-> loc :group))

(defn group-nodes
  "Returns sequence of group nodes from current loc"
  [loc]
  (map zip/node (groups loc)))


; General purpose

(defn- pretty
  "Default function for returning map for use in print-table"
  [[node group]]
  (clojure.set/rename-keys
    (conj (select-keys node [:tag])
          (select-keys (:attrs group) [:dtype :count]))
   {:count "Count" :dtype "Group" :tag "Scope" }))

(defn pretty-summary
  "Uses clojure.pprint to print-table information about the loc nodes.
  The following setting overrides may be supplied:
  :ppred pred - takes a single argument (loc) predicate to determine if
                a summary table for this node should be printed.
  :efunc func - a function that takes a tuple (vector) of the loc node and
                a group node and returns a map ready for print-table
  :cpred pred - a single argument predicate that is passed the collection
                of children locations for each group of current loc to determine
                if the processing should continue."
  [loc & settings]
  (let [_groups (children loc)
        {:keys [ppred efunc cpred]
         :or {ppred #(= % %)
              efunc pretty
              cpred #((complement empty?) %) } :as args} settings]
    (when (ppred loc)
      (print-table (map #(efunc [(zip/node loc) (zip/node %)]) _groups)))
    (doseq [_gs  _groups
            :let [_ch (children _gs)]
            :when (cpred _ch)]
      (doseq [_node _ch
              :when ((complement empty?) (:content (zip/node _node)))]
        (pretty-summary _node
                        :ppred ppred
                        :efunc efunc
                        :cpred cpred)))))

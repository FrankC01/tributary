(ns ^{:author "Frank V. Castellucci"
      :doc "tributary utility functions"}
  tributary.utils
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml])
  )


(defn- xtype
  "Hack to determine namespace (XPDL | BPMN)"
  [zip]
  (let [_h (clojure.string/split (name (:tag (first zip))) #":")
        _ns  (if (= (count _h) 1) nil (_h 0))
        _nsk (if (nil? _ns) :xmlns (keyword (str "xmlns:"_ns)))
        _t   (_nsk (select-keys (:attrs (first zip)) [_nsk]))
        _type (clojure.string/split _t #"/")
        _form (if (empty? (filter #(= % "BPMN") _type)) :xpdl :bpmn)
        ]
  (assoc {} :ns _ns :stype _form :zip zip)))

(defn parse-source
  [input-source]
  (let [_raw (xml/parse input-source)]
    (conj (xtype (zip/xml-zip _raw)) {:raw _raw})))

(defn node-for-id
  "Returns first node matching node-id in nodes collection"
  [node-id nodes]
  (first (filter #(= (:id %) node-id) nodes)))

(defn nodes-for-type
  "Returns lazy-sequence of nodes that match the node-type
  in the nodes collection"
  [node-type nodes]
  (filter #(= (:type %) node-type) nodes))


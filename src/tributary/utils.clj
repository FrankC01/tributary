(ns tributary.utils
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml])
  )


(defn- xtype
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
  (xtype (zip/xml-zip (xml/parse input-source))))

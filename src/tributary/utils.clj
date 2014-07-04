(ns ^{:author "Frank V. Castellucci"
      :doc "tributary utility functions"}
  tributary.utils
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.string :as s]
            [tributary.tzip :as tz])
  )

; Cross parse bindings

(def ^:dynamic *zip* nil)
(def ^:dynamic *prefix* nil)

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


;; Utility

(defn pref
  "Forms fully qualified keyword considering xmlns *prefix*"
  [kw]
  (if (nil? *prefix*) kw (keyword (str *prefix* kw))))

(defn nsref
  [node]
  (when node
    (let [_x (clojure.string/split node #":")]
      (condp = (count _x)
        1 (assoc {} :ns nil :ref (_x 0))
        2 (assoc {} :ns (_x 0) :ref (_x 1))
        3 (assoc {} :ns (_x 1) :ref (_x 2))))))

(defn nskw2kw
  "Takes a :ns:keyword, parses and returns ref as a keyword"
  [kw]
  (keyword (:ref (nsref (str kw)))))

(defn ptags=
  "Annotes keywords with pref prior to calling mtags="
  [& c]
  (tz/mtags= (map pref c)))

(defn kwtolckw
  "Given a keyword, returns lowercase version as keyword"
  [kw]
  (keyword (s/lower-case (name kw))))

(defn map2lckws
  "Given a map (flat or nested), strips namespaces and convert all keywords to
  lower case"
  [node]
  (clojure.walk/postwalk #(if(keyword? %)
                            (kwtolckw (nskw2kw %))
                            %) node))

;; Results block functions

(defn tagblock
  "Returns a map for node, marking it as !group with optional tag override"
  [node & [tag]]
  (conj {:tag (or tag (nskw2kw (:tag node)))
         :content nil} (select-keys node [:attrs])))

(defn tagblockwc
  [node & [tag]]
  "Same as tagblock but returns content as well"
  (assoc (tagblock node tag) :content (:content node)))

(defn tagblockmap
  "Same as tagblock but imbues atmap into entity attribute map"
  [node atmap & [tag]]
  (update-in (tagblock node tag) [:attrs] conj atmap))

(defn group-definition
  "Populates a group section for various group types and returns
  a vector of individuals in the groups content element.
  loc refers to zip location
  grpkw identifies the group
  cform is the function for each individual of loc"
  [loc grpkw cform]
  (let [_nds (or (mapv cform loc) nil)]
    {:tag :group
     :attrs {:count (count _nds) :dtype grpkw}
     :content _nds}))

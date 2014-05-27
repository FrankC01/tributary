(ns tributary.bpmn
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx])
  )

;; Data setup
(defn- dtype
  [node]
  (let [_x (clojure.string/split node #":")]
    (assoc {} :lang (_x 0) :ref (_x 1))))

(defn- dataset-def
  [ls-items]
  (for [_x ls-items
        :let [_xn (zip/node _x)
              _y (assoc {} (keyword (get-in _xn [:attrs :id]))
                   (dtype (get-in _xn [:attrs :structureRef])))]]
    _y))

(defn- dataset-objdef
  [ls-items]
  (for [_x ls-items
        :let [_xn (zip/node _x)
              _y (assoc {} (keyword (get-in _xn [:attrs :itemSubjectRef]))
                   {:name (get-in _xn [:attrs :name])
                    :collection (get-in _xn [:attrs :isCollection])
                    })]]
    _y))


(defn context
  [input-source]
  (let [xml (xml/parse input-source)
        zip (zip/xml-zip xml)
        ds  (apply merge (dataset-def (zx/xml-> zip :model:itemDefinition )))
        dr  (apply merge (dataset-objdef (zx/xml-> zip :model:process :model:dataObject )))
        ds  (map #(assoc {} (key %) (merge (val %) (val %2))) ds dr)]
    (assoc {} :dataset (apply merge ds)))
  )

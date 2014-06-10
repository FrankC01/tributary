(ns ^{:author "Frank V. Castellucci"
      :doc "tributary XPDL parser functions"}
  tributary.xpdl
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx])
  )

;; Bindings
(def ^:dynamic ^:private *zip* nil)
(def ^:dynamic ^:private *prefix* nil)

(defn context
  [parse-block]
  (binding [*zip* (:zip parse-block) *prefix* (:ns parse-block)]
    (throw (Exception. "XPDL not supported at this time"))
    #_(conj (data-context zip) (process-context zip))
    ))

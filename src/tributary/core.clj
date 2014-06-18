(ns ^{:author "Frank V. Castellucci"
      :doc "tributary - Parse BPMN and XPDL resources"}
  tributary.core
  (:require [tributary.tribpers :refer :all]
            [tributary.utils :as tu]
            [tributary.bpmn :as bpmn]
            [tributary.xpdl :as xpdl]
            )
  )

(def ^:dynamic *default-persist-store* tributary.tribpers/store)
(defn set-default-persist-store!
  "Changes the default persist function"
  [function-reference]
  {:pre [(fn? function-reference)]}
  (alter-var-root #'*default-persist-store* (constantly function-reference)))

(def ^:dynamic *default-persist-fetch* tributary.tribpers/fetch)
(defn set-default-persist-fetch!
  "Changes the default persist fetch function"
  [function-reference]
  {:pre [(fn? function-reference)]}
  (alter-var-root #'*default-persist-fetch* (constantly function-reference)))

(defn context-from-source
  "Returns the appropriate context (BPMN or XPDL) based on source input"
  [source-input]
  (let [_h (tu/parse-source source-input)]
    (if (= (:stype _h) :bpmn) (bpmn/context _h) (xpdl/context _h))))

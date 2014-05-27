(ns tributary.core
  (:require [tributary.tribpers :refer :all]
            [tributary.bpmn :as bpmn]
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


(use 'clojure.pprint)

(pprint (bpmn/context  "resources/Valid Ticket.bpmn"))

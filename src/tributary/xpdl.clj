(ns tributary.xpdl
  (:require [clojure.zip :as zip]
            [clojure.xml :as xml]
            [clojure.data.zip.xml :as zx])
  )

(defn context
  [parse-block]
  (let [zip (:zip parse-block)]
    (throw (Exception. "XPDL not supported at this time"))
    #_(conj (data-context zip) (process-context zip))
    )
  )

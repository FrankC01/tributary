(ns ^{:author "Frank V. Castellucci"
      :doc "tributary noop functions"}
  tributary.tribpers)

(defn store
  "Default persist function"
  [something]
  something)

(defn fetch
  "Default fetch function"
  [something]
  something)

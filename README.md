# tributary

A Clojure library to work with XPDL/BPMN.

## Objective

Intent is to support developers that require processing of business processes articulated in XPDL/BPMN resources.

First thoughts are to establish a context by which analyzers and execution may occur or at least advance the notion.

## Roadmap

| Release | Descritpion |
| -------: | :----------- |
| 0.1.2-SNAPSHOT   | BPMN Parse (rich type handling) |
| 0.1.1-SNAPSHOT (Current) | BPMN Parse (limited type handling) |
| 0.1.0-SNAPSHOT   | Initial Setup |


## Usage 0.1.1-SNAPSHOT
Be warned, the current state will be turbulent whilst I hone the skills!

Assuming you've cloned and installed tributary:

___Add the dependency to your project.clj___
````clojure
:dependencies [[tributary "0.1.1-SNAPSHOT"]
  ...]
````
___Add to namespace___
````clojure
(ns tributary.bpmn
  (:require [tributary.core :as trib]
    ...)
  )
````
___Parse source and generate a context___
````clojure
(def t0 (trib/context-from-source "resources/Valid TIcket-LD.bpmn"))
````

### Context (BPMN) 0.1.1-SNAPSHOT

A context is a nested map structure that represents key aspects of the parse source. Currently, tributary generates two main sections:
````clojure
; A context is a nested map structure that represents key aspects of the parse source.
; Currently, tributary has two main associations to vectors:

=> (use 'clojure.pprint)
nil

=> (def t0 (context-from-source "resources/Valid Ticket-LD.bpmn"))
#'t0

=> (pprint (keys t0))
(:processes)

````
***Anatomy*** - The folliwng is a brief description of the data content of `processes`

The context returned by tributary is collection of one or more processes as defined in the BPMN.
````clojure
; The context contains a vector of one or more business process definitions
{:processes [...]}

; Each process definition
{:name            "Acme Customer Care",
  :id             "_W9CZ8OJ3EeOci8HYVGbbUA",
  :process-data   [...],
  :flowsets       [[...] [...]]}

; :process-data is a vector of the process global data declarations, for example:

{:name         "preapproved",
  :id          "_eO2lAOWIEeOUVteQEvBH2g",
  :refid       "_hwtesO46EeOoCOm4Voc-mg",
  :lang        "java",
  :ref         "java.lang.Boolean",
  :isCollection false}

; :flowsets are a vector of vectors. Each inner vector contains:

(pprint (ffirst (:flowsets (first (:processes t0)))))

{:name     "File Ticket",
  :id      "_XCeTkOJ3EeOci8HYVGbbUA",
  :nodes   [...]}

; :nodes is an unordered vector of maps (node). Each node describes a execution node (task, gateways, events, etc.)

 [{:name       "Start Validate",
   :id         "_XFOFkOJ3EeOci8HYVGbbUA",
   :type       :startEvent}
  {:name       "Store Ticket",
   :id         "_kx8ekOWIEeOUVteQEvBH2g",
   :type       :userTask,
   :execution  [...],
   :data       [...]}
   {...}]

; :data is a vector that contains local data declarations to the node,

  [{:name         "fileDate",
    :id           "_xFxysOnxEeOuJJ86upuOlA",
    :refid        "_hxLYwu46EeOoCOm4Voc-mg",
    :lang         "java",
    :ref          "java.util.Date",
    :isCollection false}],

````

### Context (XPDL - TBD but will borrow heavily from BPMN)


## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

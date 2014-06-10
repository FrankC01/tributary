# tributary

A Clojure library to work with XPDL/BPMN.

## Objective

Intent is to support developers that require processing of business processes articulated in XPDL/BPMN resources.

First thoughts are to establish a context by which analyzers and execution may occur or at least advance the notion.

## Roadmap

| Release | Descritpion |
| -------: | :----------- |
| 0.1.1-SNAPSHOT (Complete) | BPMN Parse (limited type handling) |
| 0.1.2-SNAPSHOT (Current)  | BPMN Parse (rich type handling), expect refactoring |
| 0.1.3-SNAPSHOT   | XPDL initial foray, expect refactoring |
| 0.1.4-SNAPSHOT   | XPDL Parse (limited type handling), expect refactoring |
| 0.1.5-SNAPSHOT   | XPDL Parse (rich type handling), expect refactoring |
| 0.2.0            | First Release - added richness, zipper? features |


## Usage 0.1.2-SNAPSHOT
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

### Context (BPMN) 0.1.2-SNAPSHOT

The following describes the support and resulting output form from parsing a BPMN resource.

#### Supported BPMN statements
| Statement Type | Supported inner types and/or (Comments) | Since Version |
| -------------- | :-------- | :------------: |
| :definitions   | :process, :itemDefinition, :resource, :message :interface | 0.1.2 |
| :process       | :ioSpecification, :dataObject, :laneSet, :startEvent, :userTask, :scriptTask :sendTask :serviceTask :task, :exclusiveGateway, :endEvent :sequenceFlow |  0.1.2 |
| :interface | (see :interfaces below) | 0.1.2 |
| :resource | (see :resources below) | 0.1.2 |
| :message | (see :messages below) | 0.1.2 |
| :itemDefinition | (see :process-data and :data below)  |  0.1.1 |
| :ioSpecification | (see :process-data and :data below) |  0.1.1 |
| :dataObject | used in constructing data definitions |  0.1.1 |
| :laneSet | :lane |  0.1.1 |
| :lane | :flowNodeRef (used in constructing node definitions) |  0.1.1 |
| :startEvent | (see :nodes below) |  0.1.1 |
| :endEvent | (see :nodes below) |  0.1.1 |
| :userTask | (see :nodes, :data and :bindings below) |  0.1.1 |
| :scriptTask | | 0.1.2 |
| :sendTask | | 0.1.2 |
| :serviceTask | | 0.1.2 |
| :task | (see :nodes, :data and :bindings below) |  0.1.1 |
| :scriptTask | | 0.1.2 |
| :exclusiveGateway | (see :nodes below) |  0.1.1 |
| :sequenceFlow | (see :steps and :predicates below ) |  0.1.1 |

### Data Model

The product of `context-from-source` is a nested associative structure (OK, a map) referred to as a *context*.

````clojure
; A context contains is a nested map structure that represents key aspects of the parse source.

=> (use 'clojure.pprint)
nil

=> (def t0 (context-from-source "resources/Valid Ticket-LD.bpmn"))
#'t0

=> (pprint (keys t0))
(:processes :resources :messages :interfaces)

````
***Anatomy*** - The folliwng is a brief description of the data model

````clojure

; context contains a number of root level vectors

{:resources   [...],
  :messages   [...],
  :interfaces [...],
  :processes  [...]
  }

; :resource example

{:name "1st Level Support",
  :id "FirstLevelSupportResource"}


; :messages example

{:name "addTicket Message",
  :id "AddTicketMessage",
  :itemRef "tns:TicketItem"}


; :interface example

{:name "Product Backlog Interface"
  :implementation {:lang "java",
                    :ref "com.camunda.examples.incidentmanagement.ProductBacklog"},
  :operations [{:name "addTicketOperation",
                :id "addTicketOperation",
                :messages [{:msg-id "AddTicketMessage"}],
                :implementationRef "addTicket"}]}


; :process example

{:name            "Acme Customer Care",
  :id             "_W9CZ8OJ3EeOci8HYVGbbUA",
  :process-data   [...],
  :flowsets       [[...] [...]]}

; :process-data is an unordered vector of the process scope data declarations, for example:

[{:name         "preapproved",
  :id          "_eO2lAOWIEeOUVteQEvBH2g",
  :refid       "_hwtesO46EeOoCOm4Voc-mg",
  :kind        "Information"               ; added in 0.1.2
  :lang        "java",
  :ref         "java.lang.Boolean",
  :isCollection false},
  {...}]

; :flowsets are a vector of vectors. Each inner vector contains:

(pprint (ffirst (:flowsets (first (:processes t0)))))

{:name     "File Ticket",
  :id      "_XCeTkOJ3EeOci8HYVGbbUA",
  :nodes   [...],
  :steps   [...]}

; :nodes is an unordered vector of node declarations. Each node describes an execution node (task, gateways, events, etc.)

 [{:name       "Start Validate",
   :id         "_XFOFkOJ3EeOci8HYVGbbUA",
   :type       :startEvent}
  {:name       "Store Ticket",
   :id         "_kx8ekOWIEeOUVteQEvBH2g",
   :type       :userTask,
   :data       [...],
   :bindings   [...]}
   {...}]

; :data (for :task and :userTask types only) is an unordered vector of node local data declarations

  [{:name         "fileDate",
    :id           "_xFxysOnxEeOuJJ86upuOlA",
    :refid        "_hxLYwu46EeOoCOm4Voc-mg",
    :lang         "java",
    :ref          "java.util.Date",
    :isCollection false},
    {...}]

; :bindings (for :task and :userTask types only) is an unordered vector of node local data assignment expressions

  [{:from          "new java.util.Date()",     ; Expression
    :to-data-refid "_hxLYwu46EeOoCOm4Voc-mg"}, ; Results to data refid (see :data above)
    {...}]

; :steps is a vector containing a tree defining the sequence of steps
[{:name       "Yes"                      ; If path is named
  :predicates ["preapproved == true"],   ; Expressions determining if step can execute. :none indicates unconditional execution
  :node       "_XFOFkOJ3EeOci8HYVGbbUA", ; id of node being referenced
  :type       :exclusiveGateway,         ; Referenced node type
  :next       [...]                      ; Next step if evaluation of predicate allows
  },
  {...}]
````

### Context (XPDL - TBD but will borrow heavily from BPMN)


## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

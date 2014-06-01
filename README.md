# tributary

A Clojure library to work with XPDL/BPMN.

## Objective

Intent is to support developers that require processing of business processes articulated in XPDL/BPMN resources.

First thoughts are to establish a context by which analyzers and execution may occur or at least advance the notion.

## Usage
Be warned, the current state will be turbulent whilst I hone the skills!

Assuming you've cloned and installed tributary:
1. Add the dependency to your project.clj
````clojure
:dependencies [[tributary "0.1.0-SNAPSHOT"]
  ...]
````
2. Add to namespace
````clojure
(ns tributary.bpmn
  (:require [tributary.core :as trib]
    ...)
  )
````
3. Parse source and generate a context
````clojure
(def t0 (trib/context-from-source "resources/Valid TIcket.bpmn"))
````

### Context

A context is a nested map structure that represents key aspects of the parse source. Currently, tributary generates two main sections:
````clojure
; A context is a nested map structure that represents key aspects of the parse source.
; Currently, tributary has two main associations to vectors:

=> (use 'clojure.pprint)
nil

=> (def t0 (context-from-source "resources/Valid Ticket.bpmn"))
#'t0

=> (pprint (keys t0))
(:processes :datarefs)

````
#### :datarefs
... is a vector of maps where each map is a data definition. Currently, the bpmn procesesor only ferets out the globally defined. The following are examples from the "Valid Ticket" bpmn resource:

````clojure
=> (pprint (:datarefs t0))
[{:data-inputref-id "_-dsIQOcuEeOCUsvrzncABg",
  :collection false,
  :data-objectref-id
  "DataObject_-dq6IOcuEeOCUsvrzncABg_eO2lAOWIEeOUVteQEvBH2g",
  :name "preapproved",
  :lang "java",
  :ref "java.lang.Boolean",
  :id "_eO2lAOWIEeOUVteQEvBH2g"}
 {:data-inputref-id "_-dt9c-cuEeOCUsvrzncABg",
  :collection false,
  :data-objectref-id
  "DataObject_-dt9cucuEeOCUsvrzncABg_f6fncOWIEeOUVteQEvBH2g",
  :name "name",
  :lang "java",
  :ref "java.lang.String",
  :id "_f6fncOWIEeOUVteQEvBH2g"}
 {:data-inputref-id "_-dt9eecuEeOCUsvrzncABg",
  :collection false,
  :data-objectref-id
  "DataObject_-dt9eOcuEeOCUsvrzncABg_h7J3oOWIEeOUVteQEvBH2g",
  :name "date",
  :lang "java",
  :ref "java.util.Date",
  :id "_h7J3oOWIEeOUVteQEvBH2g"}]
````

#### :processes
... is a vector of maps where each map corresponds to a 'flow' (*lane* in bpmn). A flow contains a tree of 'steps'.
The following are examples from the "Valid Ticket" bpmn lane:

````clojure
=> (pprint (:processes t0))
[{:flow
  {:step
   [{:step
     [{:step [{:name "Terminate end event2",
               :spec-type :endEvent,
               :id "_hozt8OWJEeOUVteQEvBH2g"}],
       :condition [],
       :name "File It",
       :spec-type :userTask,
       :id "_kx8ekOWIEeOUVteQEvBH2g"}],
     :condition [{:expression ["preapproved == true"], :ref "java.lang.Boolean", :lang "java"}],
     :name "Valid Ticket",
     :spec-type :exclusiveGateway,
     :id "_3Zx8YOWIEeOUVteQEvBH2g"}
    {:step [{:name "Terminate end event1",
             :spec-type :endEvent,
             :id "_tzk3IOWIEeOUVteQEvBH2g"}],
     :condition [{:expression ["preapproved == false"], :ref "java.lang.Boolean", :lang "java"}],
     :name "Valid Ticket",
     :spec-type :exclusiveGateway,
     :id "_3Zx8YOWIEeOUVteQEvBH2g"}],
   :condition [{:expression :any, :ref "java.lang.Boolean", :lang "java"}],
   :name "Start Validate",
   :spec-type :startEvent,
   :id "_XFOFkOJ3EeOci8HYVGbbUA"},
  :name "Ticket Filing",
  :id "_XCeTkOJ3EeOci8HYVGbbUA"}]
````

## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

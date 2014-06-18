# tributary

A Clojure library for parsing BPMN and/or XPDL.

## Objective

Intent is to support developers that require processing of business processes articulated in XPDL/BPMN resources.

First thoughts are to establish a context by which analyzers and execution may occur or at least advance the notion.

## Roadmap

| Release | Descritpion |
| -------: | :----------- |
| ~~0.1.1-SNAPSHOT (Complete)~~ | ~~BPMN Parse (limited type handling)~~ |
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
:dependencies [[tributary "0.1.2-SNAPSHOT"]
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
; Parsing in the BPMN source
; In our test fixture we use

(def t0 (context-from-source
  (-> "Incident Management.bpmn"
      clojure.java.io/resource
      clojure.java.io/file)))

; and have the following profile in project.clj

:profiles {:dev {:source-paths ["dev/src"]
                 :resource-paths ["dev/resources"]}}
````

### Description (BPMN) 0.1.2-SNAPSHOT

The data model has changed significantly in 0.1.2. The following descripes the mapping
and support for BPMN for this project version:

#### BPMN support and mapping
If it is not listed here, it is not yet supported...

| BPMN Term   | BPMN children   | tributary keyword | Comment  |
| :--------   | :-------------  | :------        | :------- |
| definitions | dataStore, itemDefinition, message, resource, interface, process | | see following |
| definitions |  | :definition    | attribute map of definitions xml statement |
| dataStore | | :data-stores | collection on context |
| itemDefinition || :items | collection on context |
| message  | | :messages  | collection on context |
| resource | | :resources | collection on context |
| interface| | :interfaces | collection on context |
| process  | event, task, gateway, dataObject, ioSpecification, dataStoreReference, callActivity laneSet, sequenceFlow, dataStoreReference | :processes  | see following |
| callActivity | | :process-nodes | on each :process individual |
| event    | startEvent, endEvent, boundaryEvent, intermediateThrowEvent, intermediateCatchEvent | :process-nodes | on each :processes individual |
| task     | task, userTask, scriptTask, sendTask, receiveTask, serviceTask, subProcess | :process-nodes | on each :processes individual |
| gateway  | exclusiveGateway, parallelGateway, inclusiveGateway, eventBasedGateway | :process-nodes | on each :processes individual |
| ioSpecification | dataInput, dataOutput | :data  | on each :process-node if declared |
| dataObject | | :process-object-refs | on each :processes individual |
| ioSpecification | dataInput, dataOutput | :process-data  | on each :processes individual if declared in source |
| laneSet  | lane, flowNodeRef | :process-flow-refs| on each :processes individual if declared in source |
| sequenceFlow | | :process-flows | on each :processes individual if declared in source |
| dataStoreReference | | :process-store-refs | on each :processes individual if declared in source |

### Context Data Model

The context forms the data DSL for consumption and is the product of `context-from-source` execution.

````clojure
; context examples using Incident Management.bpmn

=> (use 'clojure.pprint)
nil

=> (def t0 (context-from-source
     (-> "Incident Management.bpmn"
     clojure.java.io/resource
     clojure.java.io/file)))
#'t0

=> (pprint (:definition t0))
{:targetNamespace "http://fox.camunda.com/model/98a0678d9e194de9b3d9284886c3",
 :name "Incident Management",
 :typeLanguage "http://jcp.org/en/jsr/detail?id=270",
 :expressionLanguage "http://www.jcp.org/en/jsr/detail?id=245",
 :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
 :xmlns "http://www.omg.org/spec/BPMN/20100524/MODEL",
 :xmlns:dc "http://www.omg.org/spec/DD/20100524/DC",
 :xmlns:tns "http://fox.camunda.com/model/98a0678d9e194de9b3d9284886c3",
 :xmlns:java "http://jcp.org/en/jsr/detail?id=270",
 :id "_98a0678d9e194de9b3d9284886c3",
 :xmlns:di "http://www.omg.org/spec/DD/20100524/DI",
 :xmlns:bpmndi "http://www.omg.org/spec/BPMN/20100524/DI"}

=> (pprint (keys t0))
(:definition :data-stores :items :messages :resources :interfaces :processes)

````
***Anatomy*** - Context highlevel and :processes lower level maps described (key orders manually edited for readability)

````clojure

; context contains a definition map and a number of definition level vectors (typically of maps)

{:definition   {...}       ; Map of namespace assignments
  :data-stores [{...}]     ; Vector of dataStore definitions
  :items       [{...}]     ; Vector of itemDefinition definitions
  :messages    [{...}]     ; Vector of message definitions
  :resources   [{...}]     ; Vector of resource definitions
  :interfaces  [{...}]     ; Vector of interface definitions
  :processes   [{...}]     ; Vector of process definitions
  }

; :processes is a vector of process maps

(pprint (nth (:processes t0) 0))

{:id                    string       ; e.g. "WFP-1-1",
  :isExecutable         string       ; "true" | "false",
  :process-store-refs   [{...}],     ; Vector of store references
  :process-object-refs  [{...}],     ; Vector of process scoped dataObjects
  :process-flow-refs    [{...}],     ; Vector of nested maps (parent = laneSet,
                                     ;   children = lanes, grandchildren = flowNodeRef)
  :process-nodes        [{...}],     ; Vector of nodes (events, tasks, etc.)
  :process-flows        [{...}],     ; Vector of execution trees (derived from sequenceFlows)
  :process-data         [{...}]}     ; Vector of process scope data declarations

; :process-flows has 0 or more execution trees  and references to process-nodes
; :predicates are conditional expressions or :none, that gate execution of the step
; :node describes the type and id (can be used to lookup in :process-nodes)

[{:predicates [:none],
  :next
  [{:predicates [:none],
    :next
    [{:predicates [:none],
      :next
      [{:predicates
        [" ${getDataObject(\"TicketDataObject\").status == \"Open\"} "],
        :arc-name "2nd level issue",
        :next
        [{:predicates [:none],
          :next
          [{:predicates
            [" ${getDataObject(\"TicketDataObject\").status == \"Deferred\"} "],
            :arc-name "Fix in Next release",
            :next
            [{:predicates [:none],
              :next
              [{:predicates [:none],
                :next
                [{:predicates [:none],
                  :next
                  [{:arc-name "",
                    :node {:id "_1-376", :type :endEvent},
                    :next [],
                    :predicates [:none]}],
                  :node {:type :scriptTask, :id "_1-201"}}],
                :node {:type :sendTask, :id "_1-150"}}],
              :node {:type :serviceTask, :id "_1-325"}}],
            :node {:type :exclusiveGateway, :id "_1-303"}}
           {:predicates
            [" ${getDataObject(\"TicketDataObject\").status == \"Resolved\"} "],
            :arc-name "Issue resolved",
            :next
            [{:predicates [:none],
              :next
              [{:predicates [:none],
                :next
                [{:arc-name "",
                  :node {:id "_1-376", :type :endEvent},
                  :next [],
                  :predicates [:none]}],
                :node {:type :scriptTask, :id "_1-201"}}],
              :node {:type :sendTask, :id "_1-150"}}],
            :node {:type :exclusiveGateway, :id "_1-303"}}],
          :node {:type :userTask, :id "_1-252"}}],
        :node {:type :exclusiveGateway, :id "_1-128"}}
       {:predicates
        [" ${getDataObject(\"TicketDataObject\").status == \"Resolved\"} "],
        :arc-name "Issue resolved",
        :next
        [{:predicates [:none],
          :next
          [{:predicates [:none],
            :next
            [{:arc-name "",
              :node {:id "_1-376", :type :endEvent},
              :next [],
              :predicates [:none]}],
            :node {:type :scriptTask, :id "_1-201"}}],
          :node {:type :sendTask, :id "_1-150"}}],
        :node {:type :exclusiveGateway, :id "_1-128"}}],
      :node {:type :userTask, :id "_1-77"}}],
    :node {:type :scriptTask, :id "_1-26"}}],
  :node {:type :startEvent, :id "_1-13"}}]
````

### Context (XPDL - TBD but will borrow heavily from BPMN)


## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

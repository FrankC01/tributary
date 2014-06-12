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
(def t0 (trib/context-from-source "resources/Incident Management.bpmn"))
````

### Description (BPMN) 0.1.2-SNAPSHOT

The data model has changed significantly in 0.1.2. The following descripes the mapping
and support for BPMN for this project version:

#### BPMN support and mapping
If it is not listed here, it is not yet supported...

| BPMN Term | tributary term | Comment | Since |
| :-------- | :------------- | :------ | -----:|
| definitions | context | the result of parsing BPMN xml | 0.1.1 |
| definitions | :definition | attribute map of definitions xml statement | 0.1.2 |
| itemDefinition | :items | collection on context | 0.1.2 |
| message  | :messages | collection on context | 0.1.2 |
| resource | :resources | collection on context | 0.1.2 |
| interface | :interfaces | collection on context | 0.1.2 |
| process   | :processes  | colleciton on context | 0.1.2 |
| ioSpecification, dataInput, dataOutput | :process-data | collection process level data on individual of :processes | 0.1.2 |
| startEvent, endEvent, task, userTask, scriptTask, sendTask, serviceTask, exclusiveGateway | :process-nodes | collection of process type references on individual of :processes | 0.1.2 |
| laneSet   | :process-flow-refs | collection on individual of :processes | 0.1.2 |
| lane      | :flow-refs | collection on individual of :process-flow-refs | 0.1.2 |
| flowNodeRef | :node-refs | collection on individual of :flow-refs | 0.1.2 |
| ioSpecification, dataInput, dataOutput | :data | collection on individual of :process-nodes | 0.1.2 |
| ioSpecification, dataInputAssociation, dataOutputAssociation | :bindings | collection on individual of :process-nodes | 0.1.2 |

### Context Data Model

The context forms the data DSL for consumption and is the product of `context-from-source` execution.

````clojure
; context examples using Incident Management.bpmn

=> (use 'clojure.pprint)
nil

=> (def t0 (context-from-source "resources/Incident Management.bpmn))
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
(:definition :items :messages :resources :interfaces :processes)

````
***Anatomy*** - Each of the highlevel and deeper level maps demonstrated

````clojure

; context contains a number of root level vectors typically of map(s)

{:definition  {...}
  :items      [{...} {...}]
  :messages   [{...} {...}]
  :resources  [{...} {...}]
  :interfaces [{...} {...}]
  :processes  [{...} {...}]
  }

; :items

[{:id "IssueItem",
  :structureRef {:ref "com.camunda.examples.incidentmanagement.IssueReport", :ns nil},
  :itemKind "Information",
  :isCollection false}
 {...}]

; :resources

[{:name "1st Level Support", :id "FirstLevelSupportResource"},
 {...}]

; :messages

[{:name "addTicket Message", :id "AddTicketMessage", :itemRef "tns:TicketItem"},
 {...}]

; :interface example, note the nested array of operations and messages

[{:name "Product Backlog Interface"
  :implementation {:lang "java", :ref "com.camunda.examples.incidentmanagement.ProductBacklog"},
  :operations [{:name "addTicketOperation",
                :id "addTicketOperation",
                :messages [{:msg-id "AddTicketMessage"}],
                :implementationRef "addTicket"}]},
  {...}]

; :processes is a vector of process contexts

(pprint (first (:processes t0)))

[{:id                   "WFP-1-1",
  :isExecutable         "true",
  :process-flow-refs    [...],
  :process-nodes        [...],
  :process-flows        [...],
  :process-data         [...]},
  {...}]

; :process-flow-refs - summary of laneSet, lanes and flowNodeRefs

[{:id "ls_1-1"
  :flow-refs [{:node-refs ["_1-13" "_1-26" "_1-77" "_1-128" "_1-150" "_1-201" "_1-376"],
               :name "1st level support",
               :partitionElementRef "tns:FirstLevelSupportResource",
               :id "_1-9"}
             {...}],
  }]

; :process-nodes, an unordered vector of nodes (startEvent, task, etc.) assocated with process

[{:id "_1-77",
  :name "edit 1st level ticket",
  :type :userTask,
  :owner-resource ["FirstLevelSupportResource"],
  :data [{:item-id {:ref "TicketItem", :ns "tns"},    ; reference into :process-data
          :id "TicketDataOutputOf_1-77",              ; node data local identity
          :reftype :output,                           ; used as :input or :output
          :scope :task}                               ; scope as :task or :process
          {:item-id {:ref "TicketItem", :ns "tns"},
          :id "TicketDataInputOf_1-77",
          :reftype :input,
          :scope :task}],
  :bindings [{:from nil,
              :to-data-refid "TicketDataInputOf_1-77"}]
  }
  {...}]


; :bindings (for :task and :userTask types only) is an unordered vector of node local data assignment expressions

  [{:from          "new java.util.Date()",     ; Expression
    :to-data-refid "_hxLYwu46EeOoCOm4Voc-mg"}, ; Results to data refid (see :data above)
    {...}]

; :process-flows is execution tree referenings the process-nodes
; :predicates are expressions or :none, that gate execution of the step

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

# tributary

A Clojure library for parsing BPMN and/or XPDL.

## Objective

Intent is to support developers that require processing of business processes articulated in XPDL/BPMN resources.

First thoughts are to establish a context by which analyzers and execution may occur or at least advance the notion.

## Roadmap

| Release | Descritpion |
| -------: | :----------- |
| 0.1.2-SNAPSHOT (Complete)  | BPMN Parse (rich type handling), expect refactoring |
| 0.1.3-SNAPSHOT (Up next)   | XPDL initial foray, expect refactoring |
| 0.1.4-SNAPSHOT   | XPDL Parse (limited type handling), expect refactoring |
| 0.1.5-SNAPSHOT   | XPDL Parse (rich type handling), expect refactoring |
| 0.2.0            | First Release - add quality, richness and features |


## Usage 0.1.2-SNAPSHOT
The latest makes a significant change from previous data models. I've opted to align more to the original parse xml format for a number of reasons:

1. Closer to the original source
2. Straight forward zippers
3. Use of data.zip.xml selector filtering


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

(def s0 (context-from-source
  (-> "Nobel Prize Process.bpmn"
      clojure.java.io/resource
      clojure.java.io/file)))

; and have the following profile in project.clj

:profiles {:dev {:source-paths ["dev/src"]
                 :resource-paths ["dev/resources"]}}
````

### Description (BPMN) 0.1.2-SNAPSHOT

The data model has changed significantly in 0.1.2 from the ad-hoc map model I was creating to the standard XML structure to take advantage of build-in zippers and other utilities. The following descripes the mapping
and support for BPMN for this project version:

### Context Data Model

The context forms the data DSL for consumption and is the product of `context-from-source` execution.

````clojure
; context examples using Nobel Prize Process.bpmn

=> (use 'clojure.pprint)
nil

=> (def s0 (context-from-source
     (-> "Nobel Prize Process.bpmn"
     clojure.java.io/resource
     clojure.java.io/file)))
#'s0

=> (pprint (:attrs s0))
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
***Anatomy*** - Context highlevel and lower level maps described (key orders manually edited for readability)

````clojure
; See /dev/src/user.clj for some example results, navigation and  general usage
````

### Context (XPDL - TBD but will borrow heavily from BPMN)


## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

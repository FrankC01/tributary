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
{:id "_1276276944297",
 :targetNamespace "http://www.trisotech.com/definitions/_1276276944297",
 :xmlns:xsi "http://www.w3.org/2001/XMLSchema-instance",
 :xmlns:di "http://www.omg.org/spec/DD/20100524/DI",
 :xmlns:bpmndi "http://www.omg.org/spec/BPMN/20100524/DI",
 :xmlns:dc "http://www.omg.org/spec/DD/20100524/DC",
 :xmlns:semantic "http://www.omg.org/spec/BPMN/20100524/MODEL"}
````
#####Anatomy

The tributary context returned from `context-from-source` is discused with examples.
######Groups

Groups are first class nodes and exists at a number of different levels. Below is an examples of the groups associated to the context and process nodes generated using the zipper root location and the `pretty-summary` function. During execution you would want to use the raw zipper calls (also shown) to use the information.


````clojure
(ns ^{:author "Frank V. Castelluci"
      :doc "Development only sand-box"}
  tributary.user
  (:require [tributary.core :refer :all]
            [tributary.tzip :as tz]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.pprint :refer :all]
   ))

(def s0 (context-from-source
     (-> "Nobel Prize Process.bpmn"
     clojure.java.io/resource
     clojure.java.io/file)))

(def z0 (zip/xml-zip s0))

; Context (root) summary

==> (tz/pretty-summary z0 :ppred #(contains? #{:context} (:tag (zip/node %))))

|    Scope | Count |      Group |
|----------+-------+------------|
| :context |    10 |   :message |
| :context |     0 |  :resource |
| :context |     4 |     :store |
| :context |     0 |      :item |
| :context |     0 | :interface |
| :context |     4 |   :process |

; Same using raw zipper
==> (pprint (zx/xml-> _z0 :group (comp #(select-keys % [:dtype :count]) :attrs zip/node)))

({:count 10, :dtype :message}
 {:count 0, :dtype :resource}
 {:count 4, :dtype :store}
 {:count 0, :dtype :item}
 {:count 0, :dtype :interface}
 {:count 4, :dtype :process})

; Process (in :process group) summary
==> (tz/pretty-summary z0 :ppred #(contains? #{:process} (:tag (zip/node %))))

|    Scope | Count |     Group |
|----------+-------+-----------|
| :process |     1 |     :data |
| :process |     4 |    :store |
| :process |     1 | :sequence |
| :process |    13 |     :node |

; Same using raw zipper

==> (pprint (take 4 (zx/xml-> _z0 tz/groups :process :group
      (comp #(select-keys % [:dtype :count]) :attrs zip/node) )))

({:count 1,  :dtype :data}
 {:count 4,  :dtype :store}
 {:count 1,  :dtype :sequence}
 {:count 13, :dtype :node})

````

### Context (XPDL - TBD but will borrow heavily from BPMN)


## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

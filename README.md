# tributary

A Clojure library for parsing BPMN and/or XPDL.

## Objective

Intent is to support developers that require processing of business processes articulated in XPDL/BPMN resources.

First thoughts are to establish a context by which analyzers and execution may occur or at least advance the notion.

## Roadmap

| Release | Descritpion |
| -------: | :----------- |
|~~0.1.2-SNAPSHOT (Complete)~~  | ~~BPMN Parse (rich type handling), expect refactoring~~ |
| 0.1.3-SNAPSHOT (Current)   | XPDL initial foray, expect refactoring |
| 0.1.4-SNAPSHOT   | XPDL Parse (rich type handling), expect refactoring |
| 0.1.5-SNAPSHOT   | Normalize data model |
| 0.2.0            | First Release - add quality, richness and features |


## Usage 0.1.3-SNAPSHOT
The latest makes a significant change from previous data models. I've opted to align more to the original parse xml format for a number of reasons:

1. Closer to the original source
2. Straight forward zippers
3. Use of data.zip.xml selector filtering


Assuming you've cloned and installed tributary:

___Add the dependency to your project.clj___

````clojure
:dependencies [[tributary "0.1.3-SNAPSHOT"]
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
; Parsing in the XPDL source
; In our test fixture we use

(def xpdl (context-from-source
  (-> "HealthCare WF.xpdl"
      clojure.java.io/resource
      clojure.java.io/file)))

; and have the following profile in project.clj

:profiles {:dev {:source-paths ["dev/src"]
                 :resource-paths ["dev/resources"]}}
````

### Description (BPMN) 0.1.3-SNAPSHOT

The data model has changed significantly back in 0.1.2 from the ad-hoc map model I was creating to the standard XML structure to take advantage of build-in zippers and other utilities. The following descripes the mapping and support for XPDL for this project version:

### Context Data Model

The context forms the data DSL for consumption and is the product of `context-from-source` execution.

````clojure
; context examples using Nobel Prize Process.bpmn

=> (use 'clojure.pprint)
nil

=> (def xpdl (context-from-source
     (-> "HealthCare WF.xpdl"
     clojure.java.io/resource
     clojure.java.io/file)))
#'xpdl

=> (pprint (:attrs xpdl))
{:schemalocation "http://www.wfmc.org/2008/XPDL2.1 http://www.wfmc.org/standards/docs/bpmnxpdl_31.xsd",
 :id "tf002",
 :xsi "http://www.w3.org/2001/XMLSchema-instance",
 :xmlns "http://www.wfmc.org/2008/XPDL2.1",
 :xpdl "http://www.wfmc.org/2008/XPDL2.1",
 :created "2009-02-25 07:50:07",
 :vendor "Together",
 :xpdlversion "1.0"}
````
#####Anatomy

The tributary context returned from `context-from-source` is discused with examples.
######Groups

Groups are first class nodes and exists at a number of different levels. Below is an examples of the groups associated to the context and process nodes generated using the zipper root location and the `pretty-summary` function. During execution you would want to use the raw zipper calls (also shown) to use the information.


````clojure
(ns ^{:author "Frank V. Castelluci"
      :doc "Development only spikes"}
  tributary.user
  (:require [tributary.core :refer :all]
            [tributary.tzip :as tz]
            [tributary.utils :as tu]
            [clojure.zip :as zip]
            [clojure.data.zip.xml :as zx]
            [clojure.pprint :refer :all]
   ))
(def xpdl (context-from-source
     (-> "HealthCare WF.xpdl"
     clojure.java.io/resource
     clojure.java.io/file)))

(def xzip (zip/xml-zip xpdl))

; Context (root) summary

==> (tz/pretty-summary xzip :ppred #(contains? #{:context} (:tag (zip/node %)))))

|    Scope | Count |      Group |
|----------+-------+------------|
| :context |     0 |  :resource |
| :context |    37 | :interface |
| :context |     0 |   :message |
| :context |     0 |     :store |
| :context |    51 |      :item |
| :context |    14 |   :process |

; Same using raw zipper
==> (pprint (zx/xml-> xzip :group (comp #(select-keys % [:dtype :count]) :attrs zip/node)))

({:count 0,  :dtype :resource}
 {:count 37, :dtype :interface}
 {:count 0,  :dtype :message}
 {:count 0,  :dtype :store}
 {:count 51, :dtype :item}
 {:count 14, :dtype :process})

; Process (in :process group) sample summary
==> (tz/pretty-summary xzip :ppred #(contains? #{:process} (:tag (zip/node %))))

|    Scope | Count |      Group |
|----------+-------+------------|
| :process |     0 |  :resource |
| :process |     0 |      :data |
| :process |     1 | :parameter |
| :process |     1 |  :sequence |
| :process |    21 |      :node |

|    Scope | Count |      Group |
|----------+-------+------------|
| :process |     0 |  :resource |
| :process |     0 |      :data |
| :process |     1 | :parameter |
| :process |     1 |  :sequence |
| :process |    10 |      :node |

; Same using raw zipper sample

==> (pprint (take 5 (zx/xml-> xzip tz/groups :process :group
      (comp #(select-keys % [:dtype :count]) :attrs zip/node) )))

({:count 0,  :dtype :resource}
 {:count 0,  :dtype :data}
 {:count 1,  :dtype :parameter}
 {:count 1,  :dtype :sequence}
 {:count 21, :dtype :node})

````



## License

Copyright © 2014 Frank V. Castellucci.

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.

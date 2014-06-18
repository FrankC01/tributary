(defproject tributary "0.1.2-SNAPSHOT"
  :description "BPMN and XPDL parser and context generator"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/data.zip "0.1.1"]
                 ]
  :profiles {:dev {:source-paths ["dev/src"]
                   :resource-paths ["dev/resources"] }}
  )

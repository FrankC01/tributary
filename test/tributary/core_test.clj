(ns tributary.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [tributary.core :refer :all]))

(defn one-time-setup []
  (def incident (context-from-source
           (-> "Incident Management.bpmn"
               io/resource
               io/file)))

  (def nobel (context-from-source
            (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  )

(defn one-time-teardown []
  (ns-unmap *ns* 'incident)
  )

(defn once-fixture [f]
  (one-time-setup)
  (f)
  (one-time-teardown))

(use-fixtures :once once-fixture)

(deftest excp-test
    (is (thrown? IllegalArgumentException
                 (context-from-source
                  (-> "Does Not Exist.bpmn"
                      io/resource
                      io/file))))
  )

(deftest count-test
  (testing "incident general counts"
    (are [cntxt kw c] (= (count (kw cntxt)) c)
         incident keys 7
         incident :items 3
         incident :data-stores 0
         incident :messages 3
         incident :resources 2
         incident :interfaces 2
         incident :processes 1
         incident :definition 12))

  (testing "nobel prize general counts"
    (are [cntxt kw c] (= (count (kw cntxt)) c)
         nobel keys 7
         nobel :items 0
         nobel :data-stores 4
         nobel :messages 10
         nobel :resources 0
         nobel :interfaces 0
         nobel :processes 4
         nobel :definition 7))

  (testing "single process deeper counts"
    (are [cntxt x kw c] (= (count (kw (nth (:processes cntxt) x))) c)
         incident 0 :process-data 1
         incident 0 :process-object-refs 1
         incident 0 :process-store-refs 0
         incident 0 :process-nodes 10
         incident 0 :process-flows 1
         incident 0 :process-flow-refs 1
         ))
  )


(run-tests)

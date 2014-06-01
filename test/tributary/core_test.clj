(ns tributary.core-test
  (:require [clojure.test :refer :all]
            [tributary.core :refer :all]))

(defn one-time-setup []
  (def t0 (context-from-source "resources/Valid TIcket.bpmn"))
  (println "one time setup"))

(defn one-time-teardown []
  (ns-unmap *ns* 't0)
  (println  "one time teardown"))

(defn once-fixture [f]
  (one-time-setup)
  (f)
  (one-time-teardown))

(use-fixtures :once once-fixture)

(deftest a-test
  (testing "general Valid Ticket counts"
    (are [cntxt kw c] (= (count (kw cntxt)) c)
         t0 :datarefs 3
         t0 :processes 1)
    ))


(run-tests)

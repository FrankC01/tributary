(ns tributary.core-test
  (:require [clojure.test :refer :all]
            [clojure.java.io :as io]
            [tributary.core :refer :all]
            [clojure.zip :as zip]
            [tributary.tzip :as tz]
            [clojure.data.zip.xml :as zx]))

(defn one-time-setup []

  (def nobel (context-from-source
            (-> "Nobel Prize Process.bpmn"
               clojure.java.io/resource
               clojure.java.io/file)))

  (def nobzip (zip/xml-zip nobel))

  )

(defn one-time-teardown []
  (ns-unmap *ns* 'nobel)
  (ns-unmap *ns* 'nobzip)
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
    (are [cntxt kw c] (= (count (zx/xml-> cntxt tz/groups kw)) c)
         nobzip :message 10
         nobzip :item 0
         nobzip :store 4
         nobzip :resource 0
         nobzip :interface 0
         nobzip :process 4))


  )


(run-tests)

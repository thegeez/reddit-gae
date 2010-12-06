(ns redditongae.datastore-util
  (:use  [appengine.datastore.service :only (prepare-query)]
         appengine.datastore.protocols)
  (:import (com.google.appengine.api.datastore Query FetchOptions$Builder)))


(defn execute-query-limit [#^Query query limit]
  (-> (prepare-query query)
        (.asQueryResultIterator (FetchOptions$Builder/withLimit limit))
        (iterator-seq)
        (->>
         (map deserialize))))

(ns redditongae.datastore-util
  (:use  [appengine.datastore.service :only (prepare-query)]
         appengine.datastore.protocols)
  (:import (com.google.appengine.api.datastore Query FetchOptions$Builder)))

(defprotocol QueryLimitProtocol
  (execute-limit [query limit]
           "Execute the query against the datastore, with limit"))

(defn- execute-query-limit [#^Query query limit]
  (-> (prepare-query query)
        (.asQueryResultIterator (FetchOptions$Builder/withLimit  2))
        (iterator-seq)
        (->>
         (map deserialize))))

(extend-type Query
  QueryLimitProtocol
  (execute-limit [query limit]
                 (execute-query-limit query limit)))

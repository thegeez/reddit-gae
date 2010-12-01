(defproject reddit-gae "0.2.0-SNAPSHOT"
  :description "Reddit clone in Clojure to be run on GAE"
  :namespaces [redditongae.core]
  :dependencies [[org.clojure/clojure "1.2.0"]
                 [org.clojure/clojure-contrib "1.2.0"]
                 [compojure "0.5.1"]
                 [ring "0.3.2"]
                 [hiccup "0.3.0"]
                 [appengine "0.4.1-SNAPSHOT"]
                 [joda-time "1.6"]]
  :dev-dependencies [[swank-clojure "1.2.1"]
                     [com.google.appengine/appengine-api-labs "1.3.8"]
                     [com.google.appengine/appengine-api-stubs "1.3.8"]
                     [com.google.appengine/appengine-testing "1.3.8"]]
  :compile-path "war/WEB-INF/classes"
  :library-path "war/WEB-INF/lib")

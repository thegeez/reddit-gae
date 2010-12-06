(ns redditongae.datastore-session
  "Ring session store using Google Appengine Datastore."
  (:use ring.middleware.session.store)
  (:require [appengine.datastore :as ds])
  (:import java.util.UUID))


;; from: https://github.com/smartrevolution/clj-gae-datastore
(defn to-sexpr-text
  [obj]
  (binding [*print-dup* true]
    (com.google.appengine.api.datastore.Text. (print-str obj))))

;; from: https://github.com/smartrevolution/clj-gae-datastore
(defn from-sexpr-text
  [#^com.google.appengine.api.datastore.Text t]
  (read-string (.getValue t)))


(ds/defentity Session ()
  ((skey :key identity)
   (data :serialize to-sexpr-text
         :deserialize from-sexpr-text)))

(deftype GAEDataSessionStore []
  SessionStore
  (read-session [_ skey]
                (:data (ds/find-entity (ds/make-key "session" skey)) {}))
  (write-session [_ skey data]
                 (let [skey (or skey (str (UUID/randomUUID)))]
                   ; get existing session for skey or create new
                   ; session for skey
                   (-> (or (ds/find-entity (ds/make-key "session" skey))
                           (session {:skey skey}))
                       (assoc :data data)
                       (ds/save-entity))
                   skey))
  (delete-session [_ skey]
                  ; this should be done using
                  ; only the skey (perhaps in java)
                  (ds/delete-entity (ds/make-key "session" skey))
                  nil))

(defn gae-session-data-store
  "Creates an session storage engine, mapped onto GAE Datastore."
  []
  (GAEDataSessionStore.))

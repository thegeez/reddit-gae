(ns redditongae.core
  (:gen-class :extends javax.servlet.http.HttpServlet)
  (:use compojure.core
        [ring.util.servlet   :only [defservice]]
        [ring.util.response  :only [redirect]]
        [hiccup.core         :only [h html]]
        [hiccup.page-helpers :only [doctype include-css link-to xhtml-tag]]
        [hiccup.form-helpers :only [form-to text-area text-field submit-button]]
        redditongae.datastore-util
        )
  (:require [compojure.route          :as route]
            [appengine.datastore :as ds]
            [appengine.datastore.query :as dsq]
            [appengine.users          :as users])
  (:import (java.util Date)
           (org.joda.time DateTime Duration Period)))


; ds/defentity does not allow #() inline
(defn- joda->javadate [jodadate] (.toDate jodadate))
(defn- java->jodadate [javadate] (DateTime. javadate))

(ds/defentity Link ()
  ((url :key identity)
   (title)
   (points)
   (date :serialize joda->javadate
         :deserialize java->jodadate)))

(def formatter
     (.toPrinter (doto (org.joda.time.format.PeriodFormatterBuilder.)
		   .appendDays    (.appendSuffix " day "    " days ")
		   .appendHours   (.appendSuffix " hour "   " hours ")
		   .appendMinutes (.appendSuffix " minute " " minutes ")
		   .appendSeconds (.appendSuffix " second " " seconds "))))

(defn pprint [stamp]
  (let [retr   (StringBuffer.)
        ; added 1 second offset because new links get empty time
        period (Period. (Duration. stamp (.plusSeconds (DateTime.) 1)))]
    (.printTo formatter retr period (java.util.Locale. "US"))
    (str retr)))

;; Need to add a limit
(defn links-by-points []
  (ds/select "link" order-by (:points :desc) (:date :asc)))


; lets limit this to 2 results max
(defn links-by-time []
  (-> (dsq/compile-select "link" order-by (:date :desc) (:points :desc))
      (execute-limit 2)))

(defn render-links [links]
  (println "LINKS: " links)
  (for [link links]
    (let [{:keys [url title points date]} link]
      [:li#linkinfo
       (link-to url title)
       [:span (format " Posted %s ago. %d %s " (pprint date) points "points")
        (form-to [:post "/up/"]
                 [:input {:type "hidden" :name "url" :value url}]
                 (submit-button "up"))
        (form-to [:post "/down/"]
                 [:input {:type "hidden" :name "url" :value url}]
                 (submit-button "down"))]])))

(defn top-bar []
  [:div#topbar
   [:span#user
    (if-let [user (users/current-user)]
      [:span "Logged in as " [:b (:nickname user)] " "
       (link-to (users/logout-url "/") "Logout")]
      (link-to (users/login-url "/") "Login"))]
   [:span#nav "Navigation" [:a {:href "/"} "Refresh"] [:a {:href "/new/"} "Add link"]]])

(defn reddit-new-link [msg]
  (html
   (doctype :xhtml-strict)
   [:head
    [:title "Reddit.Clojure.GAE - Submit to our authority"]]
   [:body
    [:h1 "Reddit.Clojure.GAE - Submit a new link"]
    [:h3 "Submit a new link"]
    (when msg [:p {:style "color: red;"} msg])
    (form-to [:post "/new/"]
     [:input {:type "Text" :name "url" :value "http://" :size 48 :title "URL"}]
     [:input {:type "Text" :name "title" :value "" :size 48 :title "Title"}]
     (submit-button "Add link"))
    (link-to "/" "Home")]))

(defn reddit-home []
  (html
   (doctype :xhtml-strict)
   [:head
    [:title "Reddit.Clojure.GAE"]
    (include-css "/css/main.css")]
   [:body
    [:h1 "Reddit.Clojure.GAE"]
    (top-bar)
    [:h1 "Highest ranking list"]
    [:ol (render-links (links-by-points))]
    [:h1 "Latest link"]  
    [:ol (render-links (links-by-time))]]))

(defn invalid-url? [url]
  (or (empty? url)
      (not (try (java.net.URL. url) (catch Exception e nil)))))

(defn add-link [title url]
  (redirect
   (cond
    (invalid-url? url) "/new/?msg=Invalid URL"
    (empty? title)     "/new/?msg=Invalid Title"
    (ds/find-entity (ds/make-key "link" url))  "/new/?msg=Link already submitted"
    :else
    (do
      (ds/save-entity (link {:url url :title title :date (DateTime.) :points 1}))
      "/"))))

(defn rate [url mfn]
  (let [link (ds/find-entity (ds/make-key "link" url))]
    (println "Rating is: " url (:points link))
    (ds/update-entity link {:points (mfn (:points link))})
    (redirect "/")))


;; Routes that map to simple datastore actions
;; This is to be able to run this actions within the proper setup/thread
(defroutes ds-test-routes
  (GET "/ds/save" []
       (do (ds/save-entity (link {:title "Test Link" :url "www.test.nl"
                                  :points 1 :date (DateTime.)}))
           (redirect "/")))
  (GET "/ds/show" []
       (do (println (ds/select "link"))
           (redirect "/")))
  (GET "/ds/find/*" {{url "*"} :params}
       (let [e (ds/find-entity (ds/make-key "link" url))]
         (println "Checked for existing entity w/ url: " url " result: " e)
         (redirect "/"))))

(defroutes public-routes
  ds-test-routes
  (GET "/" [] (reddit-home))
  (GET  "/new/*" {{msg "msg"} :params} (reddit-new-link msg)))



(defroutes loggedin-routes
  (POST "/new/" [url title] (add-link title url))
  (POST "/up/" [url] (rate url inc))
  (POST "/down/" [url] (rate url dec)))

(defn wrap-requiring-loggedin [application]
  (fn [request]
    (if (users/current-user)
      (application request)
      {:status 403 :body "Access denied. You must be logged in user!"})))

(wrap! loggedin-routes
       wrap-requiring-loggedin
       users/wrap-with-user-info)

(defroutes reddit
  public-routes
  (POST "/*" []  loggedin-routes)
  (route/not-found "Page not found"))

(defn- log [msg & vals]
  (let [line (apply format msg vals)]
    (locking System/out (println line))))

(defn wrap-request-logging [handler]
  (fn [{:keys [request-method uri] :as req}]
    (let [start  (System/currentTimeMillis)
          resp   (handler req)
          finish (System/currentTimeMillis)
          total  (- finish start)]
      (log "request %s %s (%dms)" request-method uri total)
      resp)))

(wrap! reddit
       wrap-request-logging)

(defservice reddit)

(ns example.core
  (:require
   [helix.core :as hx :refer [$ <>]]
   [helix.dom :as d]
   [promesa.core :as p]
   [deepstate.action :as a]
   [deepstate.action.axios :as axios.a]
   [deepstate.navigate.react-router]
   ["react-dom/client" :as rdom]
   ["react-router-dom" :as rr]
   ["axios$default" :as axios]
   [applied-science.js-interop :as j]
   [lambdaisland.uri :as uri]
   [example.lib :refer [defnc]]
   [example.components :as c]))

(defn ->form-data
  [ev]
  (some-> ev
          (j/get :target)
          (js/FormData.)
          (j/call :entries)
          (js/Object.fromEntries)
          (js->clj :keywordize-keys true)))

(def action-context (a/create-action-context))

(a/def-action ::change-query
  [{q :q
    :as _action}]
  (fn [state]
    (assoc state ::query q)))

(a/def-action ::clear-query
  [_action]
  (fn [state] (assoc state ::query "")))

(def search-url-base
  "http://localhost:8889/")

(defn search-url
  [action]
  (assoc
   (uri/uri search-url-base)
   :query
   (uri/map->query-string action)))

(defn search-query
  [{:as action}]
  (let [url (search-url action)]
    (-> url
        (axios/get)
        (p/then #(js->clj % :keywordize-keys true)))))

(axios.a/def-axios-action ::search
  [{:as action}]
  {::a/axios
   (search-query action)})

(defnc Layout
  []
  (d/section
   {:class "container"}
   (d/section
    {:class "mt-5 mb-5 text-center"}
    (d/h1 "Deepstate Example")
    (d/div "Search"))

   (d/section
    {:class "ps-5 pe-5"}
    ($ rr/Outlet))

   (d/section
    {:class "mt-5 mb-5 text-center"}
    (d/a
     {:href "https://github.com/mccraigmccraig/deepstate"}
     "GitHub"))))

(defnc Home
  []
  (let [a (a/use-action action-context)
        q (a/use-action-state action-context [::query])
        {search-status ::a/status
         :as r} (a/use-action-state action-context [::search])]

    (js/console.info "Home" (pr-str r))

    (<>

     (d/form
      {:on-submit
       (fn [ev]
         (.preventDefault ev)
         (let [form-data (->form-data ev)]
           (a/dispatch
            a
            (merge
             form-data
             {::a/action ::search}))))}

      (c/input
       {:name :query
        :input-type "text"
        :label-text "Query"
        :help-text (str "Enter some text")
        :value (or q "")
        :on-change (fn [ev]
                     (a/dispatch
                      a
                      {::a/action ::change-query
                       :q (j/get-in ev [:target :value])}))
        :on-clear (fn [_ev] (a/dispatch a ::clear-query))})

      (c/submit-button
       "Search"))

     (condp = search-status
       ::a/inflight (d/div "in-flight")
       ::a/success (d/div "OK")
       ::a/error (d/div "error")
       (d/div "NONE")))))

(defn create-browser-router
  []
  (rr/createBrowserRouter
   (clj->js

    [{:element (a/action-context-provider
                {:context action-context
                 :initial-arg {}
                 :children [($ Layout)]})

      :children
      [{:path "/"
        :element ($ Home)}]}])))

(def router (create-browser-router))

(defnc App
  []

  ($ rr/RouterProvider
     {:router router}))

(defonce root (rdom/createRoot (js/document.getElementById "app")))

(defn ^:export start
  []
  (.render root ($ App)))

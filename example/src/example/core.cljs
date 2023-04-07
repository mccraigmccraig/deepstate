(ns example.core
  (:require
   [helix.core :as hx :refer [$ <>]]
   [helix.hooks :as hooks]
   [helix.dom :as d]
   [promesa.core :as p]
   [deepstate.action :as a]
   [deepstate.action.axios :as axios]
   [deepstate.navigate.react-router]
   ["react-dom/client" :as rdom]
   ["react-router-dom" :as rr]
   [example.lib :refer [defnc]]))

(def action-context (a/create-action-context))

(defnc Layout
  []
  (let [a (a/use-action action-context)]

    (d/section
     {:class "container"}
     (d/section
      {:class "mt-5 mb-5 text-center"}
      (d/h1 "Deepstate"))

     (d/section
      {:class "ps-5 pe-5"}
      ($ rr/Outlet))

     (d/section
      {:class "mt-5 mb-5 text-center"}
      "Footer"))))

(defnc Home
  []
  (d/div "HOME"))

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

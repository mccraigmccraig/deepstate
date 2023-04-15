(ns example.core
  (:require
   [clojure.string :as string]
   [applied-science.js-interop :as j]
   [helix.core :as hx :refer [$ <>]]
   [helix.dom :as d]
   [promesa.core :as p]
   [deepstate.action :as a]
   [deepstate.action.async :as a.a]
   [deepstate.navigate.react-router]
   [example.components :as c]
   [example.lib :refer [defnc]]
   ["react-dom/client" :as rdom]
   ["react-router-dom" :as rr]))

(defn todo [id title]
  {:id id
   :title title
   :completed? false})

(defn all-complete? [todos]
  (every? :completed? todos))

;; a last-request-wins async action ... results
;; from requests issued before the last (active)
;; request will be discarded
(a.a/def-async-action ::add
  [state

   {p-id ::a/id
     :as _curr-async-action-state}

   {new-todo ::a/data
    n-id ::a/id
    :as _new-async-action-state}

   {title ::title
    :as _action}]

  ;; fake a random network delay
  (p/delay (+ 1000 (rand-int 5000))
           (todo (random-uuid) title))

  ;; on completion add the new todo
  (if (not= p-id n-id)
    (do
      (js/console.warn "out of order" (pr-str p-id) (pr-str n-id))
      {::a/fix-state state})

    {::a/state (update state ::todos conj new-todo)}))

(a/def-state-action ::remove
  [{todos ::todos
    :as state}
   {id ::id
    :as _action}]
  (assoc state ::todos (into [] (remove #(= (:id %) id)) todos)))

(a/def-state-action ::toggle
  [{todos ::todos
    :as state}
   {id ::id
    :as _action}]
  (assoc state ::todos (into []
                             (map #(if (= (:id %) id)
                                     (update % :completed? not)
                                     %))
                             todos)))

(a/def-state-action ::update-title
  [{todos ::todos
    :as state}
   {id ::id
    title ::title
    :as _action}]
  (assoc state ::todos (into []
                             (map
                              #(if (= (:id %) id)
                                 (assoc % :title (string/trim title))
                                 %))
                             todos)))

(a/def-state-action ::toggle-all
  [{todos ::todos
    :as state}
   {:as _action}]
  (let [all-complete? (all-complete? todos)]
    (assoc state ::todos (into []
                               (map #(assoc % :completed? (not all-complete?)))
                               todos))))

(a/def-state-action ::clear-completed
  [{todos ::todos
    :as state}
   {:as _action}]
  (assoc state ::todos (filterv (comp not :completed?) todos)))

(def action-ctx (a/create-action-context))

(defnc Layout
  []
  (let [[todos dispatch] (a/use-action action-ctx [::todos])

        active-todos (filter (comp not :completed?) todos)

        add-todo #(dispatch {::a/action ::add ::title (string/trim %)})
        toggle-all #(dispatch ::toggle-all)
        clear-completed #(dispatch ::clear-completed)]
    (d/div
     (d/section
      {:class "todoapp"}
      (d/header
       {:class "header"}
       (c/title)
       (c/new-todo {:on-complete add-todo}))
      (when (< 0 (count todos))
        (<>
         (d/section
          {:class "main"}
          (d/input {:id "toggle-all" :class "toggle-all" :type "checkbox"
                    :checked (all-complete? todos) :on-change toggle-all})
          (d/label {:for "toggle-all"} "Mark all as complete")
          (d/ul
           {:class "todo-list"}
           ($ rr/Outlet)
           ))
         (d/footer
          {:class "footer"}
          (d/span
           {:class "todo-count"}
           (d/strong (count active-todos))
           " items left")
          (d/ul
           {:class "filters"}
           (d/li ($ rr/NavLink {:to "/" :className (j/fn [^:js {isActive :isActive}] (when isActive "selected"))} "All"))
           (d/li ($ rr/NavLink {:to "/active" :className (j/fn [^:js {isActive :isActive}] (when isActive "selected"))} "Active"))
           (d/li ($ rr/NavLink {:to "/completed" :className (j/fn [^:js {isActive :isActive}] (when isActive "selected"))} "Completed")))
          (d/button {:class "clear-completed"
                     :on-click clear-completed} "Clear completed")))))
     (c/app-footer))))

(defn make-todo-list
  [dispatch]
  (let [remove-todo #(dispatch {::a/action ::remove ::id %})
        toggle-todo #(dispatch {::a/action ::toggle ::id %})
        update-todo-title (fn [id title]
                            (dispatch {::a/action ::update-title
                                       ::id id ::title title}))

        todo-list (fn [visible-todos]
                    (for [{:keys [id] :as todo} visible-todos]
                      (c/todo-item {:key id
                                    :on-toggle toggle-todo
                                    :on-destroy remove-todo
                                    :on-update-title update-todo-title
                                    :& todo})))]
    todo-list))

(defnc Active
  []
  (let [[todos dispatch] (a/use-action action-ctx [::todos])
        active-todos (filter (comp not :completed?) todos)
        todo-list (make-todo-list dispatch)]

    (todo-list active-todos)))

(defnc Completed
  []
  (let [[todos dispatch] (a/use-action action-ctx [::todos])
        completed-todos (filter :completed? todos)
        todo-list (make-todo-list dispatch)]

    (todo-list completed-todos)))

(defnc Default
  []
  (let [[todos dispatch] (a/use-action action-ctx [::todos])
        todo-list (make-todo-list dispatch)]

    (todo-list todos)))

(defn create-browser-router
  []
  (rr/createBrowserRouter
   (clj->js

    [{:element (a/action-context-provider
                {:context action-ctx
                 :initial-arg {::todos []}
                 :children [($ Layout)]})

      :children
      [{:path "/"
        :element ($ Default)}

       {:path "active"
        :element ($ Active)}

       {:path "completed"
        :element ($ Completed)}]}])))

(def router (create-browser-router))

(defnc App
  []
  ($ rr/RouterProvider
     {:router router}))

(defonce root (rdom/createRoot (js/document.getElementById "app")))

(defn ^:export start
  []
  (.render root ($ App)))

(ns example.components
  #?(:clj
     (:require [helix.core :as hx]))
  #?(:cljs
     (:require
      [helix.core :as hx :refer [$]]
      [helix.dom :as d]
      [helix.impl.props :as props]
      [example.lib :refer [defnc]]
      ["react-router-dom" :as rr]))
  #?(:cljs
     (:require-macros
      [example.components])))

#?(:cljs
   (defn class-attrs
     "normalize a :class attr if provided"
     ([class-default
       {class :class
        :as props}]
      (let [props (dissoc props :children :class)]
        (merge
         (cond
           (some? class)
           {:className (props/normalize-class class)}

           (some? class-default)
           {:className (props/normalize-class class-default)})
         props)))
     ([props]
      (class-attrs nil props))))

#?(:cljs
   (defnc Input
     [{id :id
       input-name :name
       input-type :input-type
       label-text :label-text
       help-text :help-text
       on-clear :on-clear
       :as props}]

     (let [id (or id (gensym "email-input-"))

           props (dissoc
                  props :id :name :input-type :label-text
                  :help-text :on-clear)]

       (d/div
        {:class "mb-3"}

        (d/label
         {:for id
          :class "form-label"}
         label-text)

        (d/div
         {:class "input-group"}
         (d/input
          {:id id
           :type "email"
           :class "form-control border border-end-0"
           :& (merge
               props
               (when (some? input-name)
                 {:name (name input-name)})
               (when (some? input-type)
                 {:type (name input-type)})
               (when (some? help-text)
                 {:aria-describedby (str id "-help")}))})
         (d/div
          {:class "input-group-append"}
          (d/a
           {:class "btn btn-outline-secondary border border-start-0"
            :& (when (some? on-clear)
                 {:on-click on-clear})}
           "X")))

        (when (some? help-text)
          (d/div
           {:id (str id "-help")
            :class "form-text"}
           help-text))))))

#?(:clj
   (defmacro input [& args]
     `(hx/$ Input ~@args)))

#?(:cljs
   (defnc InputWithClear
     []
     (d/div
      {:class "input-group"})))

#?(:cljs
   (defnc SubmitButton
     [{children :children
       :as props}]
     (d/button
      {:type "submit"
       :& (class-attrs ["btn" "btn-primary"] props)}
      children)))

#?(:clj
   (defmacro submit-button [& args]
     `(hx/$ SubmitButton ~@args)))

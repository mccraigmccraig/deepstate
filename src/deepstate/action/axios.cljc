(ns deepstate.action.axios
  #?(:clj
     (:require
      [deepstate.action :as-alias action]
      [deepstate.action.async :as action.async]))

  #?(:cljs
     (:require
      [promesa.core :as p]
      [deepstate.action :as action]
      [deepstate.action.async :as action.async]))

  #?(:cljs
     (:require-macros
      [deepstate.action.axios])))

;; you might think that :deepstate.action.api could be required with
;; an :as-alias to shorten the keywords below, but that would require
;; both :clj and :cljs :as-alias statements which compiles just fine,
;; but chokes cljdoc

#?(:cljs
   (defn parse-axios-success-response
     "parse errored axios API responses"
     [{data :data
       status :status
       content-type :content-type
       :as _r}]
     {::action/schema :api
      :deepstate.action.api/data data
      :deepstate.action.api/status status
      :deepstate.action.api/content-type content-type}))

#?(:cljs
   (defn parse-axios-error-response
     "parse successful axios API responses"
     [r]
     (let [{err-message :message
            err-stack :stack
            err-code :code
            :as err-data} (-> r (.toJSON) (js->clj :keywordize-keys true))]
       (p/rejected
        {::action/schema :api
         :deepstate.action.api/err-message err-message
         :deepstate.action.api/err-stack err-stack
         :deepstate.action.api/err-code err-code
         :deepstate.action.api/org-err err-data}))))

#?(:cljs
   (defn handle-axios-response
     "parse both axios API response branches"
     [api-promise]
     (p/handle
      api-promise
      (fn [succ err]
        (if (some? err)
          (parse-axios-error-response err)
          (parse-axios-success-response succ))))))

#?(:cljs
   (defn axios-action
     "function to perform an axios-action - an async-action with
      response parsing. invoked from expansions of the `def-axios-action` macro"
     [key
      state
      action
      axios-promise
      reaction-fn]

     (let [async-action-data-promise (handle-axios-response axios-promise)]

       (action.async/async-action
        key
        state
        action
        async-action-data-promise
        reaction-fn))))

#?(:clj
   (defmacro def-axios-action
     "define an axios based async action - it's like an
      [[deepstate.action.async/def-async-action]] with parsing
      of the axios responses to make things friendlier

      use the `::action/axios` key of the `axios-promise-or-axios-handler-map`
      to provide the form returning the axios promise"
     [key
      [_state-bindings _async-action-state-bindings _action-bindings :as bindings]
      axios-promise
      reaction-map]

     `(action.async/def-async-action-handler
        ~key
        ~bindings
        ~axios-promise
        ~reaction-map
        axios-action)))

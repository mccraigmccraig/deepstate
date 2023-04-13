(ns deepstate.action.async
  #?(:clj
     (:require
      [deepstate.action :as-alias action])

     :cljs
     (:require
      [promesa.core :as p]
      [deepstate.action :as action]))

  #?(:cljs
     (:require-macros
      [deepstate.action.async])))

#?(:cljs
   (defn async-navigate-url
     [{navigate-url ::action/navigate
       inflight-navigate-url ::action/navigate-inflight
       success-navigate-url ::action/navigate-success
       error-navigate-url ::action/navigate-error
       :as _action-map}
      action-path
      state]

     (let [{action-status ::action/status
            :as _async-action-state} (get-in state action-path)]
       (cond
         (some? navigate-url) navigate-url

         (and (= ::action/inflight action-status)
              (some? inflight-navigate-url))
         inflight-navigate-url

         (and (= ::action/success action-status)
              (some? success-navigate-url))
         success-navigate-url

         (and (= ::action/error action-status)
              (some? error-navigate-url))
         error-navigate-url))))

#?(:cljs
   (defn get-action-path
     "determine the path of the async-action-state in the global state"
     [key
      {action-path ::action/path
       :as action}]
     (or action-path
         (cond
           (sequential? key) (vec key)
           (keyword? key) [key]
           :else
           (throw
            (ex-info "key is not a seq or keyword"
                     {:key key
                      :action action}))))))

#?(:cljs
   (defn get-async-action-state
     "fetch async-action-state from global state"
     [key state action]
     (get-in state (get-action-path key action))))

#?(:cljs
   (defn async-action
     "a promise-based async action handler providing a consistent
      format for handling and recording emerging action state, and
      for interacting with navigation

      - `key` : the `::action/key` to match a `dispatch`. identifies the action,
           and is the default path of the action state in `state`
      - `state` : the global state
      - ``action` : the action value being handled
        - `::action/path` - instead of updating the `state` at `key`,
                            update the `state` at `path`
      - `handler-promise-or-async-handler-map` : a promise of a result,
           or a map `{::action/async Promise<action-result>
                      ::action/navigate[-*] <url>}`

      `state` will be updated at `::action-path` with a map with these keys:
        `::action/status` - ::inflight, ::success or ::error
        `::action/action` - the action value
        `::action/result` - the result value
        `::action/error` - any error value

       if an `::action/navigate` url is supplied then it will be used to
       navigate each time the action is handled (including
       `::action/inflight`)

       `::action/navigate-inflight`, `::action/navigate-success` and
       `::action/navigate-error` keys are also available to navigate
       only on particular conditions"
     [key
      state
      action
      handler-promise-or-async-handler-map]

     (let [{action-promise ::action/async
            :as action-map} (if (map? handler-promise-or-async-handler-map)
                              handler-promise-or-async-handler-map
                              {::action/async handler-promise-or-async-handler-map})

           ap (get-action-path key action)]

       ;; (js/console.info "async-action" (pr-str action-map))

       (let [new-state (update-in state ap
                                  merge {::action/status ::action/inflight
                                         ::action/action action})
             navigate-url (async-navigate-url action-map ap new-state)]
         (cond->

             {::action/state new-state

              ::action/later
              (p/handle
               action-promise
               (fn [r e]
                 (fn [state]
                   (let [new-state
                         (if (some? e)
                           (update-in state ap
                                      merge {::action/status ::action/error
                                             ::action/error e})

                           (update-in state ap
                                      merge {::action/status ::action/success
                                             ::action/result r
                                             ::action/error nil}))

                         navigate-url (async-navigate-url
                                       action-map
                                       ap
                                       new-state)]
                     (cond->
                         {::action/state new-state}

                       (some? navigate-url)
                       (assoc ::action/navigate navigate-url))))))}

           (some? navigate-url)
           (assoc ::action/navigate navigate-url))))))

#?(:clj
   (defmacro def-async-action
     "define an action handler to service a promise-based async action,
      setting initial async-action-state and updating async-action-state after the
      action has completed in a common schema described in `async-action`

       - `key` : the action key and the path in the `state` for
               the request status and response value data
       - `state-bindings` : fn bindings to destructure the global state map
       - `async-action-state-bindings` : fn bindings to destructure the async-action-state map
       - `action-bindings` : fn bindings to destructure the action map
       - `handler-promise-or-async-handler-map` : form returning a promise of
          the action data or a map as described in `async-action` - may
          refer to any of the destructured bindings"
     [key
      [state-bindings async-action-state-bindings action-bindings]
      handler-promise-or-async-handler-map]

     `(defmethod action/handle ~key
        [action#]

        (let [~action-bindings (action/remove-action-keys action#)]

          (fn [state#]

            (let [~state-bindings state#
                  ~async-action-state-bindings (get-async-action-state ~key state# action#)]

              (async-action
               ~key
               state#
               action#
               ~handler-promise-or-async-handler-map)))))))

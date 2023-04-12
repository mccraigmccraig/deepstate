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
            :as _action-state} (get-in state action-path)]
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
   (defn async-action
     "a promise-based async action handler providing a consistent
      format for handling and recording emerging action state, and
      for interacting with navigation

      - `key` : the `::action/key` to match a `dispatch`. identifies the action,
           and is the default path of the action state in `state`
      - `action` : the action value being handled
        - `::action/path` - instead of updating the `state` at `key`,
                            update the `state` at `path`
      - `action-promise-or-async-action-map` : a promise of a result,
           or a map `{::action/async <action-promise>
                      ::action/navigate <navigate-fn>}`

      `state` will be updated with a map with these keys:
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
      {action-path ::action/path
       :as action}
      action-promise-or-async-action-map
      state]

     (let [{action-promise ::action/async
            :as action-map} (if (map? action-promise-or-async-action-map)
                              action-promise-or-async-action-map
                              {::action/async action-promise-or-async-action-map})

           action-path (or action-path
                           (cond
                             (sequential? key) (vec key)
                             (keyword? key) [key]
                             :else
                             (throw
                              (ex-info "key is not a seq or keyword"
                                       {:key key
                                        :action action}))))]

       ;; (js/console.info "async-action" (pr-str action-map))

       (let [new-state (update-in state action-path
                                  merge {::action/status ::action/inflight
                                         ::action/action action})
             navigate-url (async-navigate-url action-map action-path new-state)]
         (cond->

             {::action/state new-state

              ::action/later
              (p/handle
               action-promise
               (fn [r e]
                 (fn [state]
                   (let [new-state
                         (if (some? e)
                           (update-in state action-path
                                      merge {::action/status ::action/error
                                             ::action/error e})

                           (update-in state action-path
                                      merge {::action/status ::action/success
                                             ::action/result r
                                             ::action/error nil}))

                         navigate-url (async-navigate-url
                                       action-map
                                       action-path
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
      setting initial state and state after the action has completed
      in a common schema described in `async-action`

       - `key` : the action key and the path in the `state` for
               the request status and response value data
       - `action-bindings` : fn bindings to destructure the action map
       - `action-promise-or-async-action-map` : form returning a promise of the
          result or a map as described in `async-action` - may
          refer to `action-bindings`"
     [key
      [state-bindings action-bindings]
      action-promise-or-async-action-map]

     `(defmethod action/handle ~key
        [action#]

        (let [~action-bindings (action/remove-action-keys action#)]

          (fn [state#]
            (let [~state-bindings state#]

              (async-action
               ~key
               action#
               ~action-promise-or-async-action-map
               state#)))))))

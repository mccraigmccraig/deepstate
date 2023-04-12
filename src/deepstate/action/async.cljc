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
   (defn async-navigate-fn
     "takes a fn of
        {`::action/state` `<action-state>`
         `::action/path` `<action-state-path-in-full-state>`
         `:deepstate/state` `<full-state>`}
      and adapts it to be a fn of the `<full-state>`

     this allows the navigate-fns supplied to `async-action` to
     easily destructure the specific state resulting from the
     action, or to examine the entire current state"

     [action-path
      {navigate-fn ::action/navigate
       inflight-navigate-fn ::action/navigate-inflight
       success-navigate-fn ::action/navigate-success
       error-navigate-fn ::action/navigate-error
       :as _action-map}]

     (let [choose-navigate-fn
           (cond
             (some? navigate-fn)
             navigate-fn

             (or (some? inflight-navigate-fn)
                 (some? success-navigate-fn)
                 (some? error-navigate-fn))
             (fn [{{action-status ::action/status
                    :as _action-state} ::action/state
                   :as async-nav-state}]

               ;; (js/console.info "nav status" (pr-str action-status))
               (cond
                 (and (= ::action/inflight action-status)
                      (some? inflight-navigate-fn))
                 (inflight-navigate-fn async-nav-state)

                 (and (= ::action/success action-status)
                      (some? success-navigate-fn))
                 (success-navigate-fn async-nav-state)

                 (and (= ::action/error action-status)
                      (some? error-navigate-fn))
                 (error-navigate-fn async-nav-state))))]

       (when (some? choose-navigate-fn)
         (fn [full-state]
           (let [action-state (get-in full-state action-path)]
             (choose-navigate-fn
              {::action/state action-state
               ::action/path action-path
               :deepstate/state full-state})))))))

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
      - `action-promise-or-action-map` : a promise of a result,
           or a map `{::action/async <action-promise>
                      ::action/navigate <navigate-fn>}`

      `state` will be updated with a map with these keys:
        `::action/status` - ::inflight, ::success or ::error
        `::action/action` - the action value
        `::action/result` - the result value
        `::action/error` - any error value

       if an `::action/navigate` fn is supplied then it will be used to
       navigate each time the action value is rendered (including
       `::action/inflight`). the navigate fn will be called with a map of
       {`::action/state` `<action-state>`
        `::action/path` `<action-state-path-in-full-state>`
        `:deepstate/state` `<full-state>`}
       so it can easily destructure the state of the action or the
       whole state, and is expected to return a url string to navigate to or
       `nil`

       `::action/navigate-inflight`, `::action/navigate-success` and
       `::action/navigate-error` keys are also available to navigate
       only on some conditions"
     [key
      {action-path ::action/path
       :as action}
      action-promise-or-action-map]

     (let [{action-promise ::action/async
            :as action-map} (if (map? action-promise-or-action-map)
                              action-promise-or-action-map
                              {::action/async action-promise-or-action-map})

           action-path (or action-path
                           (cond
                             (sequential? key) (vec key)
                             (keyword? key) [key]
                             :else
                             (throw
                              (ex-info "key is not a seq or keyword"
                                       {:key key
                                        :action action}))))

           navigate-fn (async-navigate-fn action-path action-map)]

       ;; (js/console.info "async-action" (pr-str action-map))

       (fn [state]

         (let [new-state (update-in state action-path
                                    merge {::action/status ::action/inflight
                                           ::action/action action})]
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
                                               ::action/error nil}))]
                       (cond->
                           {::action/state new-state}

                         (some? navigate-fn)
                         (assoc ::action/navigate (navigate-fn new-state)))))))}

             (some? navigate-fn)
             (assoc ::action/navigate (navigate-fn new-state))))))))

#?(:clj
   (defmacro def-async-action
     "define an action handler to service a promise-based async action,
      setting initial state and state after the action has completed
      in a common schema described in `async-action`

       - `key` : the action key and the path in the `state` for
               the request status and response value data
       - `action-bindings` : fn bindings to destructure the action map
       - `action-or-action-map` : form returning a promise of the
          result or a map as described in `async-action` - may
          refer to `action-bindings`"
     [key
      [action-bindings]
      action-or-action-map]

     `(defmethod action/handle ~key
        [action#]

        (let [~action-bindings (action/remove-action-keys action#)]

          (async-action ~key action# ~action-or-action-map)))))

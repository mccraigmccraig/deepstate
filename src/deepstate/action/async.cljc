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
   (defn ^:private choose-async-navigate-url
     [{navigate-url ::action/navigate
       inflight-navigate-url ::action/navigate-inflight
       success-navigate-url ::action/navigate-success
       error-navigate-url ::action/navigate-error
       :as effs-map}
      action-path
      state]

     (let [{action-status ::action/status
            :as _async-action-state} (get-in state action-path)

           nav-url (cond
                     (some? navigate-url) navigate-url

                     (and (= ::action/inflight action-status)
                          (some? inflight-navigate-url))
                     inflight-navigate-url

                     (and (= ::action/success action-status)
                          (some? success-navigate-url))
                     success-navigate-url

                     (and (= ::action/error action-status)
                          (some? error-navigate-url))
                     error-navigate-url)]

       (cond-> (dissoc
                effs-map
                ::action/navigate ::action/navigate-inflight
                ::action/navigate-success ::action/navigate-error)

         (some? nav-url)
         (assoc ::action/navigate nav-url)))))

#?(:cljs
   (defn ^:private get-action-path
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
   (defn ^:no-doc get-async-action-state
     "fetch async-action-state from global state"
     [key state action]
     (get-in state (get-action-path key action))))

#?(:cljs
   (defn async-action
     "a promise-based async action handler providing a consistent
      format for handling and recording emerging `async-action-state`, and
      for interacting with navigation

      - `key` : the `::action/key` to match a `dispatch`. identifies the action,
           and is the default path of the `async-action-state` in `state`
      - `state` : the global state
      - `action` : the action value being handled
        - `::action/path` - instead of updating the `state` at `key`,
                            update the `state` at `path`
      - `handler-promise-or-async-handler-map` : a promise of a result,
           or a map {`::action/async` `Promise<action-result>`
                     `::action/navigate`[-*] `<url>`}

      `async-action-state` will be updated at `::action-path` with a map
       with these keys:
        `::action/status` - ::inflight, ::success or ::error
        `::action/action` - the action value
        `::action/data` - the happy-path result of the action
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
      async-action-data-promise
      reaction-fn]

     (let [ap (get-action-path key action)

           init-state (update-in state ap
                                 merge {::action/status ::action/inflight
                                        ::action/action action})]

       ;; (js/console.info "async-action" (pr-str action-map))

       {::action/state init-state

        ::action/later
        (p/handle
         async-action-data-promise
         (fn [r e]
           (fn [state]
             (let [new-state
                   (if (some? e)
                     (update-in state ap
                                merge {::action/status ::action/error
                                       ::action/error e})

                     (update-in state ap
                                merge {::action/status ::action/success
                                       ::action/data r
                                       ::action/error nil}))

                   effs (reaction-fn new-state)]

               (-> effs
                   (choose-async-navigate-url ap new-state)
                   (merge {::action/state new-state}))))))})))

#?(:clj
   (defmacro async-action-bindings
     "set up bindings for an async action definition"
     [key
      [state-bindings action-state-bindings action-bindings]
      state
      action
      & body]

     `(let [~state-bindings ~state
            ~action-state-bindings (get-async-action-state ~key ~state ~action)
            ~action-bindings (action/remove-action-keys ~action)]

        ~@body)))

#?(:clj
   (defmacro def-async-action-handler
     "a macro to establish bindings used by other async-action macros.
      both [[def-async-action]] and [[deepstate.action.axios/def-axios-action]]
      defer to this macro to establish bindings"
     [key
      [_state-bindings _action-state-bindings _action-bindings :as bindings]
      async-action-data-promise
      reaction-map
      handler-fn]

     `(defmethod action/handle ~key
        [action#]

        (fn [state#]

          (let [async-action-data-promise# (async-action-bindings
                                            ~key
                                            ~bindings
                                            state#
                                            action#
                                            ~async-action-data-promise)

                ;; the reaction-fn can use the same bindings as the
                ;; action-data-promise, but will be invoked later in
                ;; reaction to the promise completing
                reaction-fn# (fn [reaction-state#]
                               (async-action-bindings
                                ~key
                                ~bindings
                                reaction-state#
                                action#
                                ~reaction-map))]

            (~handler-fn
             ~key
             state#
             action#
             async-action-data-promise#
             reaction-fn#))))))

#?(:clj
   (defmacro def-async-action
     "define an action handler to service a promise-based async action

      an `async-action-state` map will be initialised at a path in the global
      `state` map, and updated after the action has completed.
      `async-action-state` will have shape:

        {`::action/status` `::inflight|::success|::error`
         `::action/action` `<action-map>`
         `::action/data` `<action-data>`
         `::action/error` `<action-error>`}

       - `key` : the action key and the default path in the `state` for
               the `async-action-state`
       - `state-bindings` : fn bindings to destructure the global `state` map
       - `async-action-state-bindings` : fn bindings to destructure the `async-action-state` map
       - `handler-promise-or-async-handler-map` : form returning a promise of
          the `<action-data>` or a map with shape:
          {`::a/async` `Promise<action-data>`
           `::a/navigate`[-*] `<url>`  }
          may refer to any of the destructured bindings"
     [key
      [_state-bindings _action-state-bindings _action-bindings :as bindings]
      async-action-data-promise
      reaction-map]

     `(def-async-action-handler
        ~key
        ~bindings
        ~async-action-data-promise
        ~reaction-map
        async-action)))

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
   (defn ^:no-doc navigate-on-condition
     "reaction helper - navigate when (pred <action-status>) is true"
     ([async-action-state url pred]
      (navigate-on-condition {} async-action-state url pred))

     ([effs
       {status ::action/status
        :as _async-action-state}
       url
       pred]

      (if (pred status)
        (assoc effs ::action/navigate url)
        effs))))

#?(:cljs
   (defn navigate-on-success
     "reaction helper - navigate only on success"
     ([async-action-state url]
      (navigate-on-success {} async-action-state url))

     ([effs async-action-state url]
      (navigate-on-condition effs async-action-state url #(= ::action/success %)))))

#?(:cljs
   (defn navigate-on-error
     "reaction helper - navigate only on error"
     ([async-action-state url]
      (navigate-on-error {} async-action-state url))

     ([effs async-action-state url]
      (navigate-on-condition effs async-action-state url #(= ::action/error %)))))

#?(:cljs
   (defn navigate-on-inflight
     "reaction helper - navigate only when inflight"
     ([async-action-state url]
      (navigate-on-inflight {} async-action-state url))

     ([effs async-action-state url]
      (navigate-on-condition effs async-action-state url #(= ::action/inflight %)))))

#?(:cljs
   (defn navigate-always
     "reaction helper - always navigate"
     ([async-action-state url]
      (navigate-always {} async-action-state url))

     ([effs async-action-state url]
      (navigate-on-condition effs async-action-state url (constantly true)))))


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
   (defn async-action-handler
     "a promise-based async action handler providing a consistent
      format for handling and recording emerging `async-action-state`, and
      for interacting with navigation

      - `key` : the `::action/key` to match a `dispatch`. identifies the action,
           and is the default path of the `async-action-state` in `state`
      - `state` : the global state
      - `action` : the action value being handled
        - `::action/path` - instead of updating the `state` at `key`,
                            update the `state` at `path`
      - `async-action-data-promise` : a promise of the action data
      - `reactino-fn` : a (fn <state>) to be called when the promise completes,
          returning an effects map

      `async-action-state` will be updated at `::action-path` with a map
       with these keys:
        `::action/status` - ::inflight, ::success or ::error
        `::action/action` - the action value
        `::action/data` - the happy-path result of the action
        `::action/error` - any error value"
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
             (let [new-state (update-in
                              state
                              ap
                              merge
                              (if (some? e)
                                {::action/status ::action/error
                                 ::action/error e}
                                {::action/status ::action/success
                                 ::action/data r
                                 ::action/error nil}))

                   effs (reaction-fn new-state)]

                ;; allow the reaction definition full control of
               ;; state changes
               (merge
                {::action/state new-state}
                effs)))))})))

#?(:clj
   (defmacro async-action-bindings
     "set up bindings for an async action definition"
     [key
      [state-bindings async-action-state-bindings action-bindings]
      state
      action
      & body]

     `(let [~state-bindings ~state
            ~async-action-state-bindings (get-async-action-state ~key ~state ~action)
            ~action-bindings (action/remove-action-keys ~action)]

        ~@body)))

#?(:clj
   (defmacro def-async-action-handler
     "a macro to establish bindings used by other async-action macros.
      both [[def-async-action]] and [[deepstate.action.axios/def-axios-action]]
      defer to this macro to establish bindings"
     [key
      [_state-bindings _async-action-state-bindings _action-bindings :as bindings]
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
       - `action-bindings` : fn bindings to destructure the `action` map
       - `async-action-data-promise` : form returning a promise of the
          `<action-data>` - may use any established bindings
       - `reaction-map` : form which will be evaluated when promise completes
           returning effects including `::action/state`, `::action/dispatch` and
           `::action/navigate`"
     [key
      [_state-bindings _async-action-state-bindings _action-bindings :as bindings]
      async-action-data-promise
      reaction-map]

     `(def-async-action-handler
        ~key
        ~bindings
        ~async-action-data-promise
        ~reaction-map
        async-action-handler)))

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
      - `action` : the action map value being handled
        - `::action/path` - instead of updating the `state` at `key`,
                            update the `state` at `path`
      - `async-action-data-promise` : a promise of the action data
      - `effects-fn` : a (fn <state>) wrapping the `effects-map` form,
         to be called when the promise completes, returns an effects map

      `async-action-state` will be updated at `::action-path` with a map
       with these keys:
       ```Clojure
        `::action/status` - `::inflight` | `::success` | `::error`
        `::action/action` - the `action` map
        `::action/data` - the happy-path result of the `async-action-data-promise`
        `::action/error` - any error result of the `async-action-data-promise`
       ```"
     [key
      state
      action
      async-action-data-promise-fn
      init-effects-fn
      completion-effects-fn]

     (let [ap (get-action-path key action)

           curr-async-action-state (get-in state ap)

           ;; an id allows an effect-fn to detect whether
           ;; it's out of date
           action-id (random-uuid)
           init-async-action-state (merge
                                   curr-async-action-state
                                   {::action/id action-id
                                    ::action/status ::action/inflight
                                    ::action/action action})

           later-promise-fn (fn []
                              (p/handle
                               (async-action-data-promise-fn
                                state
                                curr-async-action-state
                                init-async-action-state)
                               (fn [r e]
                                 (fn [state]
                                   (let [curr-async-action-state (get-in state ap)
                                         new-async-action-state (if (some? e)
                                                                  {::action/id action-id
                                                                   ::action/status ::action/error
                                                                   ::action/error e}
                                                                  {::action/id action-id
                                                                   ::action/status ::action/success
                                                                   ::action/data r
                                                                   ::action/error nil})

                                         ;; calculate effects with previous state but new
                                         ;; async-action-state - so the effects calc can consider
                                         ;; the previous state vs the update
                                         {effs-state ::action/state
                                          fix-state ::action/fix-state
                                          :as effs} (completion-effects-fn
                                                     state
                                                     curr-async-action-state
                                                     new-async-action-state)

                                         new-state (or
                                                    ;; take the replace state unmodified
                                                    fix-state

                                                    ;; or update with the new async-action-state
                                                    (update-in
                                                     (or effs-state state)
                                                     ap
                                                     merge
                                                     new-async-action-state))]

                                     (merge
                                      effs
                                      {::action/state new-state}))))))

           init-effs (init-effects-fn
                      state
                      curr-async-action-state
                      init-async-action-state)

           init-effs (if (= ::action/cancel init-effs)
                       (do
                         ;; action is cancelled!
                         (js/console.info
                          "deepstate.action.async/async-action-handler - cancelled!"
                          (pr-str action))
                         nil)

                        (let [{init-effs-state ::action/state
                               init-effs-fix-state ::action/fix-state} init-effs

                              init-effs (dissoc init-effs ::action/state ::action/fix-state)

                              init-state (or
                                          init-effs-fix-state

                                          (update-in
                                           (or init-effs-state state)
                                           ap
                                           merge
                                           init-async-action-state))]

                          (merge
                           init-effs
                           {::action/state init-state})))]

       ;; (js/console.info "async-action" (pr-str action-map))

       (cond-> init-effs

         ;; only schedule the action promise if the action was not cancelled
         (some? init-effs) (assoc ::action/later (later-promise-fn))))))

#?(:clj
   (defmacro async-action-bindings
     "a macro which establishes bindings for async action definitions... both
      the `action-data-promise` and the `effects-map` can use
      these bindings to destructure the global `state`, the `async-action-state`
      and the `action` map"
     [_key
      [state-bindings
       async-action-state-bindings
       new-async-action-state-bindings
       action-bindings]
      state
      async-action-state
      new-async-action-state
      action
      & body]

     `(let [~state-bindings ~state
            ~async-action-state-bindings ~async-action-state
            ~new-async-action-state-bindings ~new-async-action-state
            ~action-bindings (action/remove-action-keys ~action)]

        ~@body)))

#?(:clj
   (defmacro def-async-action-handler
     "a macro which defines a handler for an async action, leaving
      particular behaviour to the `handler-fn`.
      [[def-async-action]] and [[deepstate.action.axios/def-axios-action]]
      both defer to this macro
      - `key` - the action key
      - `bindings` - bindings for the global `state`, the `async-action-state` and
          the `action` map
      - `async-action-data-promise` - a promise of the data for the async action
      - `init-effects-map` - a form to be evaluated before the promise is
         created
      - `completion-effects-map` - a form to be evaluated after the
         `async-action-data-promise` has completed
      - `handler-fn` - the fn implementing specific async action behaviour"
     [key
      [_state-bindings _async-action-state-bindings _action-bindings :as bindings]
      async-action-data-promise
      init-effects-map
      completion-effects-map
      handler-fn]

     `(defmethod action/handle ~key
        [action#]

        (fn [state#]

          (let [async-action-data-promise-fn# (fn [state#
                                                   async-action-state#
                                                   new-async-action-state#]
                                                (async-action-bindings
                                                 ~key
                                                 ~bindings
                                                 state#
                                                 async-action-state#
                                                 new-async-action-state#
                                                 action#
                                                 ~async-action-data-promise))

                init-effects-fn# (fn [state#
                                      async-action-state#
                                      new-async-action-state#]
                                   (async-action-bindings
                                    ~key
                                    ~bindings
                                    state#
                                    async-action-state#
                                    new-async-action-state#
                                    action#
                                    ~init-effects-map))

                ;; the effects-fn will use the same bindings as the
                ;; async-action-data-promise, but will be invoked later in
                ;; reaction to the promise completing
                completion-effects-fn# (fn [state#
                                            async-action-state#
                                            new-async-action-state#]
                                         (async-action-bindings
                                          ~key
                                          ~bindings
                                          state#
                                          async-action-state#
                                          new-async-action-state#
                                          action#
                                          ~completion-effects-map))]

            (~handler-fn
             ~key
             state#
             action#
             async-action-data-promise-fn#
             init-effects-fn#
             completion-effects-fn#))))))

#?(:clj
   (defmacro def-async-action
     "a macro which defines an action handler to service a promise-based async
      action

      an `async-action-state` map will be initialised at a path in the global
      `state` map, and updated after the action has completed.
      `async-action-state` will have shape:

      ```Clojure
        {`::action/status` `::inflight|::success|::error`
         `::action/action` `<action-map>`
         `::action/data` `<async-action-data>`
         `::action/error` `<action-error>`}
      ```

       - `key` : the action key and the default path in the `state` for
               the `async-action-state`
       - `state-bindings` : fn bindings to destructure the global `state` map
       - `async-action-state-bindings` : fn bindings to destructure the
            `async-action-state` map
       - `action-bindings` : fn bindings to destructure the `action` map
       - `async-action-data-promise` : form returning a promise of the
          `<async-action-data>` - may use any established bindings
       - `init-effects-map` : form which will be ebaluated before the promise
         is created
       - `completion-effects-map` : form which will be evaluated when the
         `async-action-data-promise` completes and
           returning `action-effects` including `::action/state`,
          `::action/dispatch` and `::action/navigate`"
     ([key
       [_state-bindings _async-action-state-bindings _action-bindings :as bindings]
       async-action-data-promise
       init-effects-map
       completion-effects-map]

      `(def-async-action-handler
         ~key
         ~bindings
         ~async-action-data-promise
         ~init-effects-map
         ~completion-effects-map
         async-action-handler))

     ([key
       [_state-bindings _async-action-state-bindings _action-bindings :as bindings]
       async-action-data-promise
       completion-effects-map]

      `(def-async-action-handler
         ~key
         ~bindings
         ~async-action-data-promise
         nil
         ~completion-effects-map
         async-action-handler))))

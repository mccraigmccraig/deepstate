(ns deepstate.action
  #?(:clj
     (:require
      [helix.core :as-alias hx]
      [helix.hooks :as-alias hooks]))
  #?(:cljs
     (:require
      ["react" :as react]
      [promesa.core :as p]
      [helix.core :as hx]
      [helix.hooks :as hooks]
      [deepstate.navigate :as nav]))
  #?(:cljs
     (:require-macros
      [deepstate.action])))

#?(:cljs
   (defmulti ^:no-doc handle
     "handle a possibly asynchronous action

      - `action` : the `ActionMap.` must have key `::action`
                 which identifies the handler multimethod

      returns:
        `ActionEffects` = (f state) ->
                            {::state <state'>
                             ::navigate <url>
                             ::dispatch <ActionMap>|[<ActionMap>]
                             ::later Promise<ActionEffects>}

      i.e. handle returns a fn of state which, when invoked,
      returns a map of (all optional) effects, including
        `::state` - updated state
        `::navigate` - a url to navigate to
        `::dispatch` - further ActionMaps to dispatch
        `::later` - a Promise of another ActionEffects fn"
     (fn [{action-key ::action
           :as _action}]

       action-key)))

#?(:cljs
   (defmethod handle :default
     [action]
     (js/console.warn "no handler matching action key: " (pr-str action))))

#?(:cljs
   (declare dispatch))

#?(:cljs
   (defn ^:no-doc apply-dispatch-effect
     [action-context-val
      dispatch-effect]
     (cond
       (map? dispatch-effect)
       (dispatch action-context-val dispatch-effect)

       (sequential? dispatch-effect)
       (doseq [deff dispatch-effect]
         (dispatch action-context-val deff))

       :else
       (throw
        (ex-info
         "unrecognised dispatch effect"
         {:dispatch-effect dispatch-effect})))))

#?(:cljs
   (defn ^:no-doc action-effects-reducer-action
     "return a reducer action which can be dispatched
      to the underlying react reducer
      `action-context-val` - action context value
      `action-effects-fn` - a (fn <state>) from (handle <ActionMap>)"
     [{react-dispatch ::react-dispatch
       set-navurl ::set-navurl
       :as action-context-val}
      action-effects-fn]

     (fn [state]
       (let [{new-state-eff ::state
              navigate-eff ::navigate
              dispatch-eff ::dispatch
              later-eff ::later
              :as _effects} (action-effects-fn state)]

         (js/console.debug
          "action-effects-reducer-action"
          (pr-str _effects))

         (when (some? navigate-eff)
           (set-navurl navigate-eff))

         (when (some? dispatch-eff)
           (apply-dispatch-effect action-context-val dispatch-eff))

         (when (some? later-eff)
           (p/handle
            later-eff
            (fn [succ err]
              (react-dispatch
               (action-effects-reducer-action
                action-context-val
                (if (some? err)
                  (fn [state] {::state (assoc state ::error err)})
                  succ))))))

         ;; return the updated state to the react reducer
         (or new-state-eff state)))))

#?(:cljs
   (defn dispatch
     "dispatch an action to update the state

       - `action-context-val` : the value from an action-context
                          provided by `action-context-provider`

       - `action` : an ActionMap. must have key `::action` which
                  identifies the handler multimethod. if the action
                  is just a keyword it will be treated as the
                  `::action` key"
     [{react-dispatch ::react-dispatch
       :as action-context-val}
      action]

     (js/console.debug
      "deepstate.action/dispatch"
      ;; (pr-str action-context-val)
      (pr-str action))

     (let [;; allow bare keywords as actions
           action (if (keyword? action)
                    {::action action}
                    action)

           action-effects-fn (handle action)]

       ;; (js/console.log "deepstate.action/dispatch action-effects"
       ;;                 (pr-str action-effects))

       (react-dispatch
        (action-effects-reducer-action
         action-context-val
         action-effects-fn))

       true)))

#?(:cljs
   #_{:clj-kondo/ignore [:unused-private-var]}
   (defn ^:private  action-fn-reducer
     "new state is simply the fn `f` applied to the old `state`"
     [state f]
     (let [new-state (f state)]
       ;; (js/console.debug "deepstate.action/action-fn-reducer" (pr-str new-state))
       new-state)))

#?(:cljs
   #_{:clj-kondo/ignore [:unused-private-var]}
   (defn ^:private make-action-context-val
     "make the value passed around in an action-context ...
      it encapsulates both the `state` and the state
      `dispatch` fn from a react `useState` hook"
     [state react-dispatch set-navurl]
     ;; (js/console.warn "make-action-context-val")
     {::state state
      ::react-dispatch react-dispatch
      ::set-navurl set-navurl}))

#?(:cljs
   (defn create-action-context
     "create the React `Context` object"
     []
     (react/createContext)))

#?(:cljs
   (defn use-action-context
     "get the value from an `action-context`"
     [ctx]
     (react/useContext ctx)))

#?(:cljs
   (defn use-action-dispatch
     "get a dispatch fn

      - `ctx` - the action context"
     [ctx]
     (let [ctx-val (react/useContext ctx)]
       (partial dispatch ctx-val))))

#?(:cljs
   (defn use-action
     "get a state value and dispatch fn

      - `ctx` - the action context
      - `path` - optional path into state for returned state value
      returns:
        [state dispatch]"
     ([ctx] (use-action ctx nil))
     ([ctx path]
      (let [{state ::state
             :as ctx-val} (react/useContext ctx)]
        [(get-in state path)
         (partial dispatch ctx-val)]))))

#?(:cljs
   (defn use-action-state
     "extract the value at `path` (default `nil`) in the current `state`
      from an `action-context`"
     ([ctx]
      (use-action-state ctx nil))

     ([ctx path]
      (let [{state ::state
             :as _ctx-val} (react/useContext ctx)]
        (get-in state path)))))

#?(:cljs
   #_{:clj-kondo/ignore [:unused-private-var]}
   (defn ^:private remove-action-keys
     "remove keys from the ::action namespace, leaving
      only the "
     [action]
     (->> action
          (remove
           (fn [[k _v]]
             (= "deepstate.action"
                (namespace k))))
          (into {}))))

#?(:clj
   (defmacro def-action
     "define a generic action
      - `key` : the action key
      - `state-bindings` : fn bindings to destructure the state
      - `action-bindings` : fn bindings to destructure the action
      - `body` : any forms which return an action `handle` ActionEffects fn
                 (i.e. a (fn <state>)). can use the `action-bindings`"
     [key
      [state-bindings action-bindings]
      & body]

     `(defmethod handle ~key
        [action#]

        (let [~action-bindings (remove-action-keys action#)]

          (fn [~state-bindings]
            ~@body)))))

#?(:clj
   (defmacro def-state-action
     "define an action with only state effects
      - `key` : the action key
      - `state-bindings` : fn bindings to destructure the state
      - `action-bindings` : fn bindings to destructure the action
      - `body` : forms for a (fn <state>)->state .
                 can use the `action-bindings`"
     [key
      [state-bindings action-bindings]
      & body]

     `(defmethod handle ~key
        [action#]

        (let [~action-bindings (remove-action-keys action#)]

          (fn [~state-bindings]
            {::state ~@body})))))

#?(:cljs
   (hx/defnc ActionContextProvider
     [{context :context
       initial-arg :initial-arg
       [child] :children}]

     ;; since this is where state is stored
     {:helix/features {:fast-refresh true}}

     (let [[state react-dispatch] (hooks/use-reducer action-fn-reducer initial-arg)

           ;; navurl will receive an optional url to navigate to after
           ;; an action is handled
           [navurl set-navurl] (hooks/use-state nil)
           val (make-action-context-val state react-dispatch set-navurl)

           navigate (nav/navigator)]

       ;; if navigate is called during a render then we get an
       ;; error from react - so collect the forward url in navurl
       ;; and navigate after render
       (hooks/use-effect
        [navurl]
        (when (some? navurl)
          (js/console.info
           "deepstate.action/action-context-provider navigating"
           navurl)
          (set-navurl nil)
          (navigate navurl)))

       (hx/provider
        {:context context
         :value val}
        child))))

#?(:clj
   (defmacro action-context-provider
     "uses a `use-reducer` hook to encapsulate state and propagates
      it with a `ContextProvider`

       - `context` - the `action-context` React context
       - `initial-arg` - the initial `state` value
       - `child` - content to include inside the `ContextProvider` element"
     [& args]

     `(hx/$ ActionContextProvider ~@args)))

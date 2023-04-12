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

;; --choices--
;;
;; (handle <ActionMap>) -> ActionEffects
;;
;; but what is ActionEffects
;;
;; --
;; should ActionEffects be a function of state ? e.g.
;;
;; `ActionEffects` = (f state) ->
;;                     {::state <state'>
;;                      ::navigate <url>
;;                      ::dispatch <ActionMap>|[<ActionMap>]
;;                      ::effects Promise<ActionEffects>}
;;
;; since we only have access to state in the reducer fn, then this
;; ActionEffects must be processed in the reducer fn - but the reducer fn
;; must be pure, and in any case has no access to enough context to dispatch,
;; so this approach is not possible
;;

#?(:cljs
   (defmulti ^:no-doc handle
     "handle a possibly asynchronous action

      - `action` : the `ActionMap.` must have key `::action`
                 which identifies the handler multimethod

      returns:
        `ActionEffects` = {::update-now (f state) -> state'
                           ::navigate (f state) -> url
                           ::dispatch <`ActionMap>`|[<`ActionMap`>]
                           ::update-later Promise<`ActionEffects`>}
                          | (f state) -> state'
                          | Promise<`ActionEffects`>

      i.e. handle returns fns of state which will be used
      to update the state. If the action is asynchronous it
      can return one fn to update the state now and return
      another when action has completed, potentially
      recursively"
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
   (defn ^:private apply-update-state-and-navigate-effects
     "returns a fn which safely applies fn `f` to `state`,
      storing any errors at key `::error`.

      if `navigate-fn` is non-nil then it will be passed
      the updated `state` and is expected to return a url
      which will be recorded in the navurl state for later
      navigation"
     [{set-navurl ::set-navurl
       :as _action-context-val}
      navigate-effect-fn
      update-state-effect-fn]

     (fn [state]
       ;; (dispatch _action-context-val ::do-nothing)
       (let [navigate (fn [state]
                        (try
                          (let [url (navigate-effect-fn state)]
                            (when (some? url)
                              ;; (js/console.info "navigating: " url)
                              (set-navurl url))
                            state)
                          (catch :default e
                            (assoc state ::error e))))

             new-state (try
                         (cond-> (update-state-effect-fn state)
                           (some? navigate-effect-fn) (navigate))
                         (catch :default e
                           (assoc state ::error e)))]

         ;; (js/console.debug
         ;;  "update-and-navigate"
         ;;  (pr-str new-state))

         new-state))))

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
   (defn ^:no-doc apply-effects-loop
     "given ActionEffects from a `(handle action)`,
      apply the effects, looping until all
      promises are resolved

      4 effects are currently supported ...
      `::update-now` - a fn to apply to `state` to update it
      `::navigate` - a fn to apply to `state` to get a url to navigate to
      `::dispatch` - an action-map or list of action-maps to dispatch
      `::update-later` - a promise of further ActionEffects"
     [{react-dispatch ::dispatch
       :as action-context-val}
      action-effects]
     (p/loop [action-effects action-effects]

       ;; (js/console.debug
       ;;  "deepstate.action/apply-effects-loop"
       ;;  (pr-str action-effects))

       (cond
         (fn? action-effects)
         (react-dispatch (apply-update-state-and-navigate-effects
                          action-context-val
                          nil
                          action-effects))

         (map? action-effects)
         (let [{update-now-eff ::update-now
                navigate-fn-eff ::navigate
                dispatch-eff ::dispatch
                update-later-eff ::update-later} action-effects]

           (when (fn? update-now-eff)
             (react-dispatch (apply-update-state-and-navigate-effects
                              action-context-val
                              navigate-fn-eff
                              update-now-eff)))

           ;; fully handle any dispatch effects before any update-later
           ;; effects
           (let [update-later-eff
                 (if (some? dispatch-eff)
                   (p/handle
                    (apply-dispatch-effect action-context-val dispatch-eff)
                    (fn [_ e]
                      (if (some? e)
                        #_{:clj-kondo/ignore [:invalid-arity]}
                        (p/recur (fn [state] (assoc state ::error e)))
                        (when (p/promise? update-later-eff)
                          #_{:clj-kondo/ignore [:invalid-arity]}
                          (p/recur update-later-eff)))))
                   update-later-eff)]
             (when (p/promise? update-later-eff)
               (p/handle
                update-later-eff
                (fn [action-effects e]
                  (if (some? e)
                    #_{:clj-kondo/ignore [:invalid-arity]}
                    (p/recur (fn [state] (assoc state ::error e)))

                    #_{:clj-kondo/ignore [:invalid-arity]}
                    (p/recur action-effects)))))))

         (p/promise? action-effects)
         (p/handle
          action-effects
          (fn [action-effects e]
            (if (some? e)
              #_{:clj-kondo/ignore [:invalid-arity]}
              (p/recur (fn [state] (assoc state ::error e)))

              #_{:clj-kondo/ignore [:invalid-arity]}
              (p/recur action-effects))))

         :else
         (p/recur
          (fn [state]
            (assoc state ::error
                   (ex-info "unrecognised action-effects"
                            {:action-effects action-effects}))))))))

#?(:cljs
   (defn dispatch
     "dispatch an action to update the state

       - `action-context-val` : the value from an action-context
                          provided by `action-context-provider`

       - `action` : the action. must have key `::action` which
                  identifies the handler multimethod. if the action
                  is just a keyword it will be treated as the
                  `::action` key"
     [action-context-val
      action]

     (js/console.debug
      "deepstate.action/dispatch"
      ;; (pr-str action-context-val)
      (pr-str action))

     (let [;; allow bare keywords as actions
           action (if (keyword? action)
                    {::action action}
                    action)

           action-effects (handle action)]

       ;; (js/console.log "deepstate.action/dispatch action-effects"
       ;;                 (pr-str action-effects))

       (apply-effects-loop action-context-val action-effects)

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
     [state dispatch set-navurl]
     ;; (js/console.warn "make-action-context-val")
     {::state state
      ::dispatch dispatch
      ::set-navurl set-navurl}))

#?(:cljs
   (defn create-action-context
     "create the React `Context` object"
     []
     (react/createContext)))

#?(:cljs
   (defn use-action
     "get the value from an `action-context`"
     [ctx]
     (react/useContext ctx)))

#?(:cljs
   (defn use-action-dispatch
     "get a dispatch fn for an `action-context`"
     [ctx]
     (let [ctx-val (react/useContext ctx)]
       (partial dispatch ctx-val))))

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
   (defn use-action-state-dispatch
     ([ctx] (use-action-state-dispatch ctx nil))
     ([ctx path]
      (let [{state ::state
             :as ctx-val} (react/useContext ctx)]
        [(get-in state path)
         (partial dispatch ctx-val)]))))

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
      - `action-bindings` : fn bindings to destructure the action
      - `body` : any forms which return an action `handle` ActionEffects
                 or a promise thereof. can use the `action-bindings`"
     [key
      [action-bindings]
      & body]

     `(defmethod handle ~key
        [action#]

        (let [~action-bindings (remove-action-keys action#)]

          ~@body))))

#?(:cljs
   (hx/defnc ActionContextProvider
     [{context :context
       initial-arg :initial-arg
       [child] :children}]

     ;; since this is where state is stored
     {:helix/features {:fast-refresh true}}

     (let [[state dispatch] (hooks/use-reducer action-fn-reducer initial-arg)

           ;; navurl will receive an optional url to navigate to after
           ;; an action is handled
           [navurl set-navurl] (hooks/use-state nil)
           val (make-action-context-val state dispatch set-navurl)

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

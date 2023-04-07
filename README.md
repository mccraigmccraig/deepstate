# deepstate

[![Clojars Project](https://img.shields.io/clojars/v/com.github.mccraigmccraig/deepstate.svg)](https://clojars.org/com.github.mccraigmccraig/deepstate)
[![cljdoc badge](https://cljdoc.org/badge/com.github.mccraigmccraig/deepstate)](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate)


A ClojureScript microlib for state management in a [Helix](https://github.com/lilactown/helix) based / React app

## The problem

React hooks and `re-frame` are from different worlds and don't play nicely 
together.

deepstate provides some simple hooks-based state management primitives which 
maybe aren't very efficient in a large app, but are simple, flexible and
straightforward in an async world.

## require

``` clojure
(require '[deepstate.action :as a])
(require '[deepstate.action.async :as a.async])
```

## The model and state

There is a single state value (per `action-context`). The state
should be pure data.

Actions get 
dispatched in response to events in the app and are then handled, 
and the result of handling an 
action is some effects, which are functions which are used to update
the state. 

## Actions

Actions are maps which describe an operation to mutate state. They have
an `::a/action` key which selects a handler, and whatever other keys 
the handler requires.

## dispatch 

An action is `dispatch`ed causing a handler to be invoked according 
to the `::a/action` key in the action. The handler
returns some `Effects` which can be:

* `(fn [state] ...)` - an `::a/update-now` function to update state
* `Promise<Effects>` - an `::a/update-later` promise of `Effects`
* a map: 
``` clojure
    {::a/update-now (fn [state] ...)
     ::a/navigate (fn [state] ...)
     ::a/update-later Promise<Effects>}
```

any `::a/update-now` fn will be immediately used to update state,
and any `::a/navigate` fn will be used to navigate. 
`::a/update-later` will be recursively waited on and processed
until all effects have been completed.

## def-action

Defines an action handler. It takes

* the `::a/action` key used in an action map
* a parameter destructuring vector for the single action map parameter 
* the body defining the effects the handler returns - which may use
  the bindings destructured from the action map

``` clojure
(a/def-action ::change-query
  [{q :q
    :as _action}]
  (fn [state]
    (assoc state ::query q)))
```

## def-async-action 

Defines a promise-based async action handler. The action is specified as 
a form returning a promise of the result. The state of the action 
will be managed and reported on with a standard data schema, with 
the result (or error) being added to the structure when the action completes. 


``` clojure
(a.async/def-async-action ::run-query
   [{q :q
     :as action_}]
  {::a/async (run-query action)
   ::a/navigate-success (fn [{{{id :id} ::a/result} ::a/state}] (str "/item/" id))})
```

This `def-async-action` will result in a map in the state at path `[::run-query]`
with the shape 

``` clojure
{::a/status ::a/inflight|::a/success|::a/error
 ::a/result ...
 ::a/error ...}
```

## action-context

A React Context which must be provided to use the hooks

## use-action 

A hook to return the `action-context` value, which must be provided to `dispatch`

## use-action-state 

A hook to access the state. Takes an optional path into the state, without which
it returns the entire state 

``` clojure
(let [;; all the state
      all-state (a/use-action-state action-context)
      
      ;; just the ::change-query path
      qr (a/use-action-state action-context [::change-query])])
```


# license

Copyright © 2023 mccraigmccraig of the clan mccraig

Distributed under the [MIT License](https://github.com/mccraigmccraig/deepstate/blob/trunk/LICENSE).


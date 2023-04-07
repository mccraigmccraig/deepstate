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

## actions

Actions are maps which describe an operation to mutate state. They have
an `::action/action` key which selects a handler, and any other keys 
the handler requires.

## dispatch 

An action is `dispatch`ed causing a handler to be invoked according 
to the `::action/action` key in the action. The handler
returns some `Effects` which can be:

* `(fn [state] ...)` - an `::action/update-now` function to update state
* `Promise<Effects>` - an `::action/update-later` promise of `Effects`
* a map: 
``` clojure
    {::action/update-now (fn [state] ...)
     ::action/navigate (fn [state] ...)
     ::action/update-later Promise<Effects>}
```

any `::action/update-now` fn will be immediately used to update state,
and any `::action/navigate` fn will be used to navigate. 
`::action/update-later` will be recursively waited on and processed
until all effects have been completed.

## def-action

Defines an action handler

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
will be managed and reported on in a standard data-structure, with 
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





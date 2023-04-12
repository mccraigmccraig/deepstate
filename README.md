# deepstate

[![Clojars Project](https://img.shields.io/clojars/v/com.github.mccraigmccraig/deepstate.svg)](https://clojars.org/com.github.mccraigmccraig/deepstate)
[![cljdoc badge](https://cljdoc.org/badge/com.github.mccraigmccraig/deepstate)](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate)


A ClojureScript microlib for state management in a [Helix](https://github.com/lilactown/helix)-based React app

## The problem

deepstate provides some simple hooks-based state management primitives which
probably aren't very performant as the single source of truth in a large app
(there are no reactions), but are simple, flexible, and straightforward
to use in an async world.

## require

``` clojure
(require '[deepstate.action :as a])
(require '[deepstate.action.async :as a.async])
```

## Model and state

deepstate reduces a stream of events onto a state value.

There are a few core concepts:

* `action-context` - a React context for some deepstate state
* `action-context-provider` - an element providing an `action-context` to
  a component tree
* `use-action` - a hook used by components to interact with state. It
  returns a `state` value and a `dispatch` function
* `dispatch` - a fn returned by the `use-action` hook which sends an
  action to be handled
* `def-action` - a macro which defines a generic action handler. There
  are more specialised variants such as
  * `def-state-action` - defines an action handler which only modifies stat:e
  * `def-async-action` - defines an action handler which runs a promise-based
       async computation and records the status and result in a standard schema
  * `def-axios-action` - an async action which parses axios results:

## A simple example

Perhaps the simplest example:

``` clojure
(a/def-state-action ::inc-counter
  [state action]
  (update state ::counter inc))

(def action-ctx (a/create-action-context))

(defnc App
 []
 (let [[counter dispatch] (a/use-action action-ctx [::counter])]
  (d/div
    (d/p "Counter: " counter)
    (d/button {:on-click (fn [_] (dispatch ::inc-counter))} "inc"))))
```

A more complex example, demonstrating the common schema for promise-based async
actions:

``` clojure
(a/def-axios-action ::fetch-apod
  [state action]
  {::a/axios (axios/get "https://api.nasa.gov/planetary/apod\?api_key\=DEMO_KEY")})

(def action-ctx (a/create-action-context))

(defnc App
  []
  (let [[{status ::a/status
          result ::a/result
          :as apod}
         dispatch] (a/use-action action-ctx [::fetch-apod])]
  (d/div
    (d/p "Status:" (str status))
    (d/p "APOD:" (pr-str result))
    (d/button {:on-click (fn [_] (dispatch ::fetch-apod))} "Fetch!"))))
```

## action-context

An `action-context` is created with `a/create-action-context`, and
passed through a component tree by an `a/action-context-provider`
element.

Components consume state through the `a/use-action` hook, which
returns `[state dispatch]` - a state value and a dispatch fn.

## Actions

Actions are maps which describe an operation to mutate state. They have
an `::a/action` key which selects a handler, and any other keys
the particular handler requires.

## dispatch

An action is `dispatch`ed causing a handler to be invoked according
to the `::a/action` key in the action. The handler
returns a function of `state`, which when invoked returns some
optional effects (returning no effects is not very useful, but 
perfectly fine):

* `::a/state` - a new state value
* `::a/navigate` - a url to navigate to
* `::a/dispatch` - an `ActionMap` | `[ActionMap]` to be dispatched
* `::a/later` - a promise of an effect fn `(fn [state] ...)`
* `Promise<Effects>` - an `::a/update-later` promise of `Effects`

It is possible to define an action handler directly, with
`(defmethod a/handle <key> [action] (fn [state] ...))`, but it's
much easier to use one of the sugar macros...

## def-action

Defines a action handler. It takes

* the `::a/action` key used in an action map
* a parameter destructuring vector for the single action map parameter
* the body defining the effects the handler returns - which may use
  the bindings destructured from the action map

``` clojure
(a/def-action ::change-query
  [state
   {q :q
    :as _action}]
  {::a/state (assoc state ::query q)
   ::a/navigate "/show-state"})
```

## def-state-action

``` clojure
(a/def-state-action ::change-query
  [state
   {q :q
    :as _action}]
  (assoc state ::query q))
```

## def-async-action

Defines a promise-based async action handler. The action is specified as
a form returning a promise of the result. The global state and the 
state of the particular action is available for destructuring.

``` clojure
(a.async/def-async-action ::run-query
   [state
    {{id :id} ::a/result
     :as action-state}
    {q :q
     :as action_}]
  {::a/async (run-query action)
   ::a/navigate-success (str "/item/" id)})
```

This `def-async-action` will result in a map in the state at path `[::run-query]`
with the shape

``` clojure
{::a/status ::a/inflight|::a/success|::a/error
 ::a/result ...
 ::a/error ...}
```

# license

Copyright © 2023 mccraigmccraig of the clan mccraig

Distributed under the [MIT License](https://github.com/mccraigmccraig/deepstate/blob/trunk/LICENSE).

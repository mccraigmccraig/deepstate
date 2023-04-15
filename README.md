# deepstate

[![Clojars Project](https://img.shields.io/clojars/v/com.github.mccraigmccraig/deepstate.svg)](https://clojars.org/com.github.mccraigmccraig/deepstate)
[![cljdoc badge](https://cljdoc.org/badge/com.github.mccraigmccraig/deepstate)](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate)


A ClojureScript microlib for state management in a [Helix](https://github.com/lilactown/helix)-based React app

## Summary

deepstate provides simple hooks-based state management primitives which
probably aren't very performant as the single source of truth in a large app
(there is nothing like
[Reagent](https://github.com/reagent-project/reagent)'s reactions'),
but are simple, flexible, and straightforward to use in an async world.

## require

``` clojure
(require '[deepstate.action :as a])
(require '[deepstate.action.async :as a.a])
(require '[deepstate.action.axios :as a.ax])
```

## Model and state

deepstate reduces a stream of events (called `action`s) onto a state value.

There are a few core concepts:

* `action-context` - a React context for some deepstate state. Hooks need to
   be passed such a context
* [action-context-provider](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action#action-context-provider) - 
   an element providing an `action-context` to a component tree
* [use-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action#use-action) - 
   a hook used by components to interact with state. It returns a `state`
   value and a `dispatch` function
* `dispatch` - a fn returned by the [[deepstate.action/use-action]] hook which
   sends an action to be handled
* [def-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action#def-action) -
  a macro which defines a generic action handler. There are more specialised
  variants such as:
  * [def-state-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action#def-state-action) - 
    defines an action handler which only modifies state
  * [def-async-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action.async#def-async-action) - 
    defines an action handler which runs a promise-based async computation and 
    records the status and result in a standard schema
  * [def-axios-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action.axios#def-axios-action) - 
    an async action for [axios](https://axios-http.com/) requests which 
    parses the responses

## A simple example

A quite simple example, showing a synchronous state-only action and an
asynchronous action.
Clicks will result in consistent data however they are interleaved:

``` clojure
(a/def-state-action ::inc-counter
  [state _action]

  (update state ::counter inc))

(a.a/def-async-action ::inc-delay
  [state
   {data ::a/data
    :as _async-action-state}
   _action]

  ;; a promise of the action data
  (promesa.core/delay 2000 5)

  ;; effects which can use the destructured action data
  or other state
  {::a/state (update state ::counter + data)})

(def action-ctx (a/create-action-context))

(defnc App
 []
 (let [[counter dispatch] (a/use-action action-ctx [::counter])]
  (d/div
    (d/p "Counter: " counter)
    (d/button {:on-click (fn [_] (dispatch ::inc-counter))} "inc")
    (d/button {:on-click (fn [_] (dispatch ::inc-delay))} "+5 delay"))))
```

Another example showing how the result of an async action can be destructured
to conditionally create effects:

``` clojure
(a.ax/def-axios-action ::fetch-apod
  [state
   {status ::a/status
    :as async-action-state}
   action]
   
  ;; a promise of the action data
  (axios/get "https://api.nasa.gov/planetary/apod\?api_key\=DEMO_KEY")
  
  ;; only create a navigate effect when successful
  (when (= ::a/success status)
    {::a/navigate "/show-pic"}))

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

Components then consume and update state through the `a/use-action` hook, which
returns `[state dispatch]` - a state value and a dispatch fn. The
`dispatch` fn is called with an `action` map, which will be handled to
generate effects.

## Actions

Actions are maps which describe an operation to change state (or perform
some other effect). They have
an `::a/action` key which selects a handler, and may have any other keys
the particular handler requires.

## dispatch

An action is `dispatch`ed causing a handler to be invoked according
to the `::a/action` key in the action. The handler
returns a function of `state`, and when that is invoked it returns
a map of (all optional) `action-effects` (returning no effects is
not very useful, but perfectly fine). The available effects are:

* `::a/state` - a new state value
* `::a/navigate` - a url to navigate to
* `::a/dispatch` - an `action-map` | [`action-map`] to be dispatched
* `::a/later` - a promise of a fn `(fn [state] ...)` -> `action-effects`

It is possible to define an action handler directly, with
`(defmethod a/handle <key> [action] (fn [state] ...))`, but it's
easier to use one of the sugar macros, which allow for some
destructuring:

## `action-effects`

There are currently 4 effects available:

* `::a/state` - a new state value
* `::a/navigate` - a url to navigate to
* `::a/dispatch` - an `action-map` | [`action-map`] to be dispatched
* `::a/later` - a promise of a fn `(fn [state] ...)` -> `action-effects`
      to provide more effects later

## [def-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action#def-action)

Defines a generic action handler. It takes

* the `::a/action` key used in an action map
* destructuring for the `state` and `action-map`
* a body form defining the effects the handler returns - which may use
  the bindings destructured from the action map

``` clojure
(a/def-action ::change-query
  [state
   {q :q
    :as _action}]
  {::a/state (assoc state ::query q)
   ::a/navigate "/show-state"})
```

## [def-state-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action#def-state-action)

Defines an action handler which only modifies state - the body form evaluates
to the updated state (i.e. not an `action-effects` map)

``` clojure
(a/def-state-action ::change-query
  [state
   {q :q
    :as _action}]
  (assoc state ::query q))
```
## [def-async-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action.async#def-async-action)

Defines a promise-based async action handler.

The body of the definition has 2 or 3 forms

1. a form returning a promise of the result data
2. (optional) initialisation effects - may also return `::a/cancel` to cancel 
   the action without evaluating the promise form
3. completion effects

The body forms are evaluated separately, and may all use bindings from
the bindings vector. Several bindings vector arities are offered:

- `[action-bindings]`
- `[state-bindings action-bindings]`
- `[state-bindings next-async-action-state-bindings action-bindings]`
- `[state-bindings async-action-state-bindings next-async-action-state-bindings action-bindings]`

So a simple async action may access the `next-async-action-state`and 
navigate on completion:

``` clojure
(a.a/def-async-action ::run-query
   [__state
    {status ::a/status
     {id :id} ::a/data
     :as _next-action-state}
    {q :q
     :as _action}]
  (run-query action)
  (when (= ::a/success status)
    {::a/navigate (str "/item/" id)}))
```

While another action may debounce by comparing the `async-action-state`
with the `next-async-action-state`:

``` clojure
(a.a/def-async-action ::debounced
   [__state
    {p-status ::a/status
     :as _action-state}
    {n-id ::a/id
     :as _next-action-state}
    {q :q
     :as _action}]
  (run-query action)
  ;; debounce if there is already an inflight query
  (when (= ::a/inflight p-status)
     ::a/cancel)
  (when (= ::a/success status)
    {::a/navigate (str "/item/" n-id)}))
```

These `def-async-action` will assoc an `async-action-state` map in the
global `state` at the action `key` path (the path can be overridden 
by providing an `::action/path` key in the `action` map), with the shape

``` clojure
{::a/id <random-uuid>
 ::a/status ::a/inflight|::a/success|::a/error
 ::a/data ...
 ::a/error ...}
```

## [def-axios-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action.axios#def-axios-action)

Exactly like [def-async-action](https://cljdoc.org/d/com.github.mccraigmccraig/deepstate/CURRENT/api/deepstate.action.async#def-async-action),
but the `action-data-promise` is expected to be an [axios](https://axios-http.com/)
promise, and the responseor error will be parsed into the `async-action-state`

# example

See the [example](https://github.com/mccraigmccraig/deepstate/tree/trunk/example)
folder in the git repo. It's a modified
[lilactown/helix-todo-mvc](https://github.com/lilactown/helix-todo-mvc) with
the state management converted to deepstate and the `::add` action being
made async with a random delay added

Build and run the example with `npm start`

# license

Copyright © 2023 mccraigmccraig of the clan mccraig

Distributed under the [MIT License](https://github.com/mccraigmccraig/deepstate/blob/trunk/LICENSE).

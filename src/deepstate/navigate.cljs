(ns deepstate.navigate)

;; there can be only one default implementation,
;; and it's not defined here ... require a
;; suitable namespace with an impl
(defmulti navigator
  (fn []))

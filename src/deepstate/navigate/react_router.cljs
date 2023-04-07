(ns deepstate.navigate.react-router
  (:require
   [deepstate.navigate :as nav]
   ["react-router-dom" :as rr]))

(defmethod nav/navigator :default
  []
  (rr/useNavigate))

(ns deepstate.navigate)

(defmulti navigator
  "return a (fn <url>) to navigate to a url

   since it is a 0-args multimethod, only the :default
   implementation can be used. require a namespace with
   the implementation you want"
  (fn []))

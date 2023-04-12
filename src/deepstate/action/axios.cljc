(ns deepstate.action.axios
  #?(:clj
     (:require
      [deepstate.action :as-alias action]))

  #?(:cljs
     (:require
      [promesa.core :as p]
      [deepstate.action :as action]
      [deepstate.action.async :as action.async]))

  #?(:cljs
     (:require-macros
      [deepstate.action.axios])))

;; you might think that :deepstate.action.api could be required with
;; an :as-alias to shorten the keywords below, but that would require
;; both :clj and :cljs :as-alias statements which compiles just fine,
;; but chokes cljdoc

#?(:cljs
   (defn parse-axios-success-response
     [{data :data
       status :status
       content-type :content-type
       :as _r}]
     {::action/schema :api
      :deepstate.action.api/data data
      :deepstate.action.api/status status
      :deepstate.action.api/content-type content-type}))

#?(:cljs
   (defn parse-axios-error-response
     [r]
     (let [{err-message :message
            err-stack :stack
            err-code :code
            :as err-data} (-> r (.toJSON) (js->clj :keywordize-keys true))]
       (p/rejected
        {::action/schema :api
         :deepstate.action.api/err-message err-message
         :deepstate.action.api/err-stack err-stack
         :deepstate.action.api/err-code err-code
         :deepstate.action.api/org-err err-data}))))

#?(:cljs
   (defn handle-axios-response
     "parse API response branches into data"
     [api-promise]
     (p/handle
      api-promise
      (fn [succ err]
        (if (some? err)
          (parse-axios-error-response err)
          (parse-axios-success-response succ))))))

#?(:cljs
   (defn axios-action
     "perform an axios-action - an async-action with
      response parsing"
     [key
      action
      axios-action-map-or-axios-action
      state]

     (let [{axios-action ::action/axios
            :as axios-action-map} (if (map? axios-action-map-or-axios-action)
                                    axios-action-map-or-axios-action
                                    {::action/axios axios-action-map-or-axios-action})

           axios-action-map (-> axios-action-map
                                (dissoc ::action/axios)
                                (assoc ::action/async
                                       (handle-axios-response axios-action)))]

       (action.async/async-action
        key
        action
        axios-action-map
        state))))

#?(:clj
   (defmacro def-axios-action
     "define an axios based async action - it's just an
      async-action with a little parsing of the axios
      responses to make things friendlier

      cf `deepstate.action.async/def-async-action`

      use the `::action/axios` key of the `action-map` to
      provide the form returning the axios promise"
     [key
      [state-bindings action-bindings]
      axios-action-map-or-axios-action]

     `(defmethod action/handle ~key
        [action#]

        (let [~action-bindings (action/remove-action-keys action#)]

          (fn [state#]
            (let [~state-bindings state#]
              (axios-action ~key action# ~axios-action-map-or-axios-action state#)))))))

(ns cljfx.ghosttyfx
  (:require [cljfx.coerce :as coerce]
            [cljfx.composite :as composite]
            [cljfx.fx.region :as fx.region]
            [cljfx.lifecycle :as lifecycle]
            [cljfx.mutator :as mutator])
  (:import [io.github.vlaaad.ghosttyfx TerminalView]
           [java.util Collection]
           [javafx.collections ObservableList]))

(set! *warn-on-reflection* true)

(defn- resettable-observable-list [get-list-fn default-list-fn]
  (let [set-all! #(.setAll ^ObservableList (get-list-fn %1) ^Collection %2)]
    (reify mutator/Mutator
      (assign! [_ instance coerce value]
        (set-all! instance (coerce value)))
      (replace! [_ instance coerce old-value new-value]
        (when-not (= old-value new-value)
          (set-all! instance (coerce new-value))))
      (retract! [_ instance _ _]
        (set-all! instance (default-list-fn instance))))))

(def props
  (merge
    fx.region/props
    (composite/props
      TerminalView
      :terminal-factory [mutator/forbidden lifecycle/scalar]
      :font [:setter lifecycle/scalar :coerce coerce/font]
      :cursor-blinking [:setter lifecycle/scalar :default true]
      :search-prompt-text [:setter lifecycle/scalar :default "Type to search..."]
      :theme [:setter lifecycle/scalar]
      :mac-option-as-alt [:setter lifecycle/scalar :default false]
      :terminal-shortcuts [(resettable-observable-list TerminalView/.getTerminalShortcuts TerminalView/.defaultTerminalShortcuts) lifecycle/scalar]
      :link-matchers [(resettable-observable-list TerminalView/.getLinkMatchers TerminalView/.defaultLinkMatchers) lifecycle/scalar]
      :on-bell [:setter lifecycle/event-handler :coerce coerce/runnable]
      :on-title-changed [:property-change-listener lifecycle/change-listener]
      :on-terminal-state-changed [:property-change-listener lifecycle/change-listener])))

(def ^:private terminal-view-lifecycle
  (lifecycle/wrap-on-delete
    (composite/describe
      TerminalView
      :ctor [:terminal-factory]
      :props props)
    TerminalView/.close))

(def view
  (lifecycle/annotate
    (lifecycle/wrap-map-desc
      lifecycle/ext-recreate-on-key-changed
      (fn recreate-on-terminal-factory-change [desc]
        {:key (:terminal-factory desc)
         :desc (assoc desc :fx/type terminal-view-lifecycle)}))
    ::view))

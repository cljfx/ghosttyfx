(ns cljfx.ghosttyfx
  (:require [cljfx.coerce :as coerce]
            [cljfx.composite :as composite]
            [cljfx.fx.region :as fx.region]
            [cljfx.lifecycle :as lifecycle]
            [cljfx.mutator :as mutator])
  (:import [io.github.vlaaad.ghosttyfx TerminalView]))

(set! *warn-on-reflection* true)

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
      :terminal-shortcuts [:list lifecycle/scalar :default []]
      :link-matchers [:list lifecycle/scalar :default []]
      :on-bell [:setter lifecycle/event-handler :coerce coerce/runnable]
      :on-title-changed [:property-change-listener lifecycle/change-listener]
      :on-terminal-state-changed [:property-change-listener lifecycle/change-listener])))

(def ^:private terminal-view-lifecycle
  (lifecycle/wrap-on-delete
    (composite/describe
      TerminalView
      :ctor [:terminal-factory]
      :props props)
    #(.close ^TerminalView %)))

(def view
  (lifecycle/annotate
    (lifecycle/wrap-map-desc
      lifecycle/ext-recreate-on-key-changed
      (fn recreate-on-terminal-factory-change [desc]
        {:key (:terminal-factory desc)
         :desc (assoc desc :fx/type terminal-view-lifecycle)}))
    ::view))

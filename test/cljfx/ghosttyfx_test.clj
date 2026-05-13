(ns cljfx.ghosttyfx-test
  (:require [cljfx.api :as fx]
            [cljfx.ghosttyfx :as ghosttyfx]
            [clojure.test :as t])
  (:import [io.github.vlaaad.ghosttyfx Terminal TerminalFactory TerminalLinkMatcher TerminalShortcut TerminalView]
           [java.io ByteArrayInputStream OutputStream]
           [java.nio.charset StandardCharsets]
           [java.util.regex Pattern]
           [javafx.scene.input KeyCombination]
           [javafx.scene.text Font]))

(defn- terminal [output close-count]
  (let [bytes (.getBytes output StandardCharsets/UTF_8)]
    (reify Terminal
      (output [_]
        (ByteArrayInputStream. bytes))
      (input [_]
        (OutputStream/nullOutputStream))
      (resize [_ _ _])
      (close [_]
        (swap! close-count inc)))))

(defn- terminal-factory [output close-count]
  (fn [_ _]
    (terminal output close-count)))

(defrecord CountingTerminalFactory [id output open-count close-count]
  TerminalFactory
  (open [_ _ _]
    (swap! open-count inc)
    (terminal output close-count)))

(defn- terminal-view-desc [terminal-factory]
  {:fx/type ghosttyfx/view
   :terminal-factory terminal-factory})

(t/deftest creates-and-deletes-terminal-view
  (let [close-count (atom 0)
        component @(fx/on-fx-thread
                     (fx/create-component
                       {:fx/type ghosttyfx/view
                        :terminal-factory (terminal-factory "hello" close-count)}))]
    (try
      (t/is (instance? TerminalView (fx/instance component)))
      (finally
        @(fx/on-fx-thread
           (fx/delete-component component))))
    (t/is (= 1 @close-count))))

(t/deftest terminal-factory-equality-controls-recreation
  (let [open-count (atom 0)
        close-count (atom 0)
        factory-1 (->CountingTerminalFactory :same "" open-count close-count)
        equal-factory (->CountingTerminalFactory :same "" open-count close-count)
        different-factory (->CountingTerminalFactory :different "" open-count close-count)
        component* (atom @(fx/on-fx-thread
                            (fx/create-component
                              (terminal-view-desc factory-1))))]
    (try
      (let [view-1 (fx/instance @component*)]
        (reset! component*
          @(fx/on-fx-thread
             (fx/advance-component
               @component*
               (terminal-view-desc equal-factory))))
        (let [view-2 (fx/instance @component*)]
          (t/is (identical? view-1 view-2))
          (t/is (= 1 @open-count))
          (let [close-count-before-recreate @close-count]
            (reset! component*
              @(fx/on-fx-thread
                 (fx/advance-component
                   @component*
                   (terminal-view-desc different-factory))))
            (let [view-3 (fx/instance @component*)]
              (t/is (not (identical? view-2 view-3)))
              (t/is (= (inc close-count-before-recreate) @close-count)))))
        (t/is (= 2 @open-count)))
      (finally
        @(fx/on-fx-thread
           (fx/delete-component @component*))))))

(t/deftest applies-terminal-view-props
  (let [close-count (atom 0)
        component @(fx/on-fx-thread
                     (fx/create-component
                       {:fx/type ghosttyfx/view
                        :terminal-factory (terminal-factory "" close-count)
                        :font {:family "Monospaced" :size 18}
                        :cursor-blinking false
                        :search-prompt-text "Search"
                        :mac-option-as-alt true}))]
    (try
      (let [^TerminalView view (fx/instance component)
            font (.getFont view)]
        (t/is (instance? Font font))
        (t/is (= "Monospaced" (.getFamily font)))
        (t/is (= 18.0 (.getSize font)))
        (t/is (false? (.isCursorBlinking view)))
        (t/is (= "Search" (.getSearchPromptText view)))
        (t/is (true? (.isMacOptionAsAlt view))))
      (finally
        @(fx/on-fx-thread
           (fx/delete-component component))))
    (t/is (= 1 @close-count))))

(t/deftest retracts-terminal-shortcuts-to-defaults
  (let [close-count (atom 0)
        terminal-factory (terminal-factory "" close-count)
        custom-shortcut (TerminalShortcut.
                          (KeyCombination/keyCombination "Shift+B")
                          (fn [] false))
        component* (atom @(fx/on-fx-thread
                            (fx/create-component
                              {:fx/type ghosttyfx/view
                               :terminal-factory terminal-factory
                               :terminal-shortcuts [custom-shortcut]})))]
    (try
      (let [^TerminalView view (fx/instance @component*)
            default-combinations (mapv #(.combination ^TerminalShortcut %)
                                   (.defaultTerminalShortcuts view))]
        (t/is (= [custom-shortcut] (vec (.getTerminalShortcuts view))))
        (reset! component*
          @(fx/on-fx-thread
             (fx/advance-component
               @component*
               {:fx/type ghosttyfx/view
                :terminal-factory terminal-factory})))
        (let [^TerminalView advanced-view (fx/instance @component*)]
          (t/is (identical? view advanced-view))
          (t/is (= default-combinations
                  (mapv #(.combination ^TerminalShortcut %)
                    (.getTerminalShortcuts advanced-view))))))
      (finally
        @(fx/on-fx-thread
           (fx/delete-component @component*))))
    (t/is (= 1 @close-count))))

(t/deftest retracts-link-matchers-to-defaults
  (let [close-count (atom 0)
        terminal-factory (terminal-factory "" close-count)
        custom-link-matcher (TerminalLinkMatcher. (Pattern/compile "issue-(\\d+)") (fn [_]))
        component* (atom @(fx/on-fx-thread
                            (fx/create-component
                              {:fx/type ghosttyfx/view
                               :terminal-factory terminal-factory
                               :link-matchers [custom-link-matcher]})))]
    (try
      (let [^TerminalView view (fx/instance @component*)]
        (t/is (= [custom-link-matcher] (vec (.getLinkMatchers view))))
        (reset! component*
          @(fx/on-fx-thread
             (fx/advance-component
               @component*
               {:fx/type ghosttyfx/view
                :terminal-factory terminal-factory})))
        (let [^TerminalView advanced-view (fx/instance @component*)]
          (t/is (identical? view advanced-view))
          (t/is (= (vec (.defaultLinkMatchers advanced-view))
                  (vec (.getLinkMatchers advanced-view))))))
      (finally
        @(fx/on-fx-thread
           (fx/delete-component @component*))))
    (t/is (= 1 @close-count))))

(ns cljfx.ghosttyfx-test
  (:require [cljfx.api :as fx]
            [cljfx.ghosttyfx :as ghosttyfx]
            [clojure.test :as t])
  (:import [io.github.vlaaad.ghosttyfx Terminal TerminalView]
           [java.io ByteArrayInputStream OutputStream]
           [java.nio.charset StandardCharsets]
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

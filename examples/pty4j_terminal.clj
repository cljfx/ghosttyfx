(ns pty4j-terminal
  (:require [cljfx.api :as fx]
            [cljfx.ghosttyfx :as ghosttyfx]
            [clojure.java.process :as process]
            [clojure.string :as str])
  (:import [com.pty4j PtyProcess PtyProcessBuilder WinSize]
           [io.github.vlaaad.ghosttyfx Shell Terminal TerminalFactory]
           [javafx.application Platform]))

(set! *warn-on-reflection* true)

(defn- windows? []
  (str/includes? (str/lower-case (System/getProperty "os.name" "")) "win"))

(defn- command [candidate]
  (some-> (try
            (process/exec (if (windows?) "where.exe" "which") candidate)
            (catch RuntimeException _))
          str/split-lines
          first
          str/trim
          not-empty
          vector))

(defn- detect-shell []
  (or (->> (if (windows?)
             ["pwsh.exe" "powershell.exe" "cmd.exe" (System/getenv "COMSPEC")]
             [(System/getenv "SHELL") "bash" "zsh" "fish" "sh"])
           (remove str/blank?)
           (some command))
      (throw (ex-info "No suitable shell found" {}))))

(defrecord PtyTerminalFactory [cmd cwd env]
  TerminalFactory
  (open [_ columns rows]
    (let [launcher (Shell/integrate cmd env)
          ^PtyProcess process (-> (PtyProcessBuilder.)
                                  (.setCommand ^String/1 (into-array String (.command launcher)))
                                  (.setConsole false)
                                  (.setRedirectErrorStream true)
                                  (.setDirectory cwd)
                                  (.setEnvironment (.environment launcher))
                                  (.setInitialColumns columns)
                                  (.setInitialRows rows)
                                  (.setUseWinConPty true)
                                  (.start))]
      (reify Terminal
        (output [_]
          (.getInputStream process))
        (input [_]
          (.getOutputStream process))
        (resize [_ columns rows]
          (.setWinSize process (WinSize. columns rows)))
        (close [_]
          (.destroy process)
          (when-not (.waitFor process 2 java.util.concurrent.TimeUnit/SECONDS)
            (.destroyForcibly process)
            (.waitFor process)))))))

(defn- root-view [{:keys [terminal-factory]}]
  {:fx/type :stage
   :showing true
   :title "cljfx/ghosttyfx pty4j example"
   :width 960
   :height 640
   :scene {:fx/type :scene
           :root {:fx/type ghosttyfx/view
                  :terminal-factory terminal-factory}}})

(defn -main [& _]
  (Platform/setImplicitExit true)
  @(fx/on-fx-thread
     (fx/create-component
       {:fx/type root-view
        :terminal-factory
        (map->PtyTerminalFactory
          {:cmd (detect-shell)
           :cwd (System/getProperty "user.dir")
           :env (System/getenv)})})))

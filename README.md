# cljfx/ghosttyfx

[![CI](https://github.com/cljfx/ghosttyfx/actions/workflows/ci.yml/badge.svg)](https://github.com/cljfx/ghosttyfx/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.github.cljfx/ghosttyfx.svg)](https://clojars.org/io.github.cljfx/ghosttyfx)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)
[![Sponsor](https://img.shields.io/badge/Sponsor-vlaaad-ea4aaa.svg?logo=github-sponsors)](https://github.com/sponsors/vlaaad)

[Cljfx](https://github.com/cljfx/cljfx) wrapper for [GhosttyFX](https://github.com/vlaaad/ghosttyfx).

## Funding

If you use cljfx/ghosttyfx in your project, please consider [sponsoring](https://github.com/sponsors/vlaaad)
its development. Your sponsorship helps keep the JavaFX integration and terminal behavior work moving.

## Installation

Get the latest version from [Clojars](https://clojars.org/io.github.cljfx/ghosttyfx):

```clojure
io.github.cljfx/ghosttyfx {:mvn/version "<version>"}
```

## Usage

GhosttyFX is pty-agnostic. Your application supplies a terminal backend by
implementing GhosttyFX's `TerminalFactory` and `Terminal` interfaces. This
example uses [pty4j](https://github.com/JetBrains/pty4j/) as the PTY backend:

```clojure
(ns my-terminal
  (:require [cljfx.api :as fx]
            [cljfx.ghosttyfx :as ghosttyfx])
  (:import [com.pty4j PtyProcess PtyProcessBuilder WinSize]
           [io.github.vlaaad.ghosttyfx Shell Terminal TerminalFactory]))

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
```

Then use `ghosttyfx/view` in a Cljfx component:

```clojure
{:fx/type ghosttyfx/view
 :terminal-factory (map->PtyTerminalFactory
                     {:cmd ["pwsh"]
                      :cwd (System/getProperty "user.dir")
                      :env (System/getenv)})}
```

`:terminal-factory` is a constructor argument. `ghosttyfx/view` uses the
factory value as its recreation key, so equality determines terminal lifecycle:
when the new factory is not equal to the previous factory, Cljfx closes the old
`TerminalView` and creates a new one.

See [examples/pty4j_terminal.clj](examples/pty4j_terminal.clj) for a complete
example, including shell detection and JavaFX application setup.

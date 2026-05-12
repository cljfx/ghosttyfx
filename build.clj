(ns build
  (:require [cemerick.pomegranate.aether :as aether]
            [clojure.tools.build.api :as b]))

(def lib 'io.github.cljfx/ghosttyfx)

(def class-dir "target/classes")

(def basis (b/create-basis {:project "deps.edn"}))

(def ghosttyfx-version
  (or (get-in basis [:libs 'io.github.vlaaad/ghosttyfx :mvn/version])
      (throw (IllegalStateException.
               "Could not find io.github.vlaaad/ghosttyfx version in basis"))))

(def version (format "%s.%s" ghosttyfx-version (b/git-count-revs nil)))

(def jar-file (format "target/%s-%s.jar" (name lib) version))

(def pom-path (format "%s/META-INF/maven/%s/pom.xml" class-dir lib))

(defn- clojars-repository [username token]
  (-> basis
      :mvn/repos
      (select-keys ["clojars"])
      (update "clojars" assoc
              :username username
              :password token)))

(defn deploy [{:keys [username token]}]
  (when-not username
    (throw (IllegalArgumentException. "Missing required :username")))
  (when-not token
    (throw (IllegalArgumentException. "Missing required :token")))
  (b/delete {:path "target"})
  (b/write-pom
    {:basis basis
     :class-dir class-dir
     :lib lib
     :version version
     :src-dirs ["src"]
     :pom-data [[:licenses
                 [:license
                  [:name "MIT License"]
                  [:url "https://opensource.org/license/mit"]]]]
     :scm {:url "https://github.com/cljfx/ghosttyfx"
           :tag (b/git-process {:git-args ["rev-parse" "HEAD"]})}})
  (b/copy-dir
    {:src-dirs ["src"]
     :target-dir class-dir})
  (b/jar {:class-dir class-dir
          :jar-file jar-file})
  (aether/deploy
    :coordinates [lib version]
    :jar-file jar-file
    :pom-file pom-path
    :repository (clojars-repository username token)))

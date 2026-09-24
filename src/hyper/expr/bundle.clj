(ns ^:no-doc hyper.expr.bundle
  "Builds an ES module with the squint core functions that h/expr output uses.
   Needs babashka.esbuild on the classpath."
  (:require [babashka.esbuild :as esbuild]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private core-dir
  (delay
    (let [dir (fs/create-temp-dir {:prefix "hyper-squint-core"})]
      (with-open [in (io/input-stream (io/resource "squint/core.js"))]
        (io/copy in (fs/file dir "core.js")))
      dir)))

(def core-js
  "Returns minified JS that exports vars, a set of munged squint core names."
  (memoize
    (fn [vars]
      (let [entry (fs/file @core-dir (str "entry-" (hash vars) ".js"))]
        (spit entry (str "export { " (str/join ", " (sort vars)) " } from './core.js';\n"))
        (-> (esbuild/build {:entry-points [(str entry)]
                            :bundle       true
                            :format       :esm
                            :minify       true})
            :outputs first :contents)))))

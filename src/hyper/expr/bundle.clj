(ns ^:no-doc hyper.expr.bundle
  "Builds an ES module with the squint core functions that h/expr output uses.
   Needs babashka.esbuild on the classpath."
  (:require [babashka.esbuild :as esbuild]
            [babashka.fs :as fs]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [hyper.brotli :as br]))

(def ^:private core-dir
  (delay
    (let [dir (fs/create-temp-dir {:prefix "hyper-squint-core"})]
      (fs/delete-on-exit dir)
      (fs/delete-on-exit (fs/file dir "core.js"))
      (fs/delete-on-exit (fs/file dir "entry.js"))
      (with-open [in (io/input-stream (io/resource "squint/core.js"))]
        (io/copy in (fs/file dir "core.js")))
      dir)))

(def ^:private last-build (atom nil))

(defn- build [vars]
  (let [entry (fs/file @core-dir "entry.js")]
    (spit entry (str "export { " (str/join ", " (sort vars)) " } from './core.js';\n"))
    (let [js (-> (esbuild/build {:entry-points [(str entry)]
                                 :bundle       true
                                 :format       :esm
                                 :minify       true})
                 :outputs first :contents)]
      {:js js :br (br/compress js :quality 11)})))

(defn core-js
  "Returns a map with :js, minified JS that exports vars, and :br, the same JS brotli-compressed.
   vars is a set of munged squint core names.
   Keeps only the build for the last vars."
  [vars]
  (let [[built-vars result] @last-build]
    (if (= vars built-vars)
      result
      (locking last-build
        (let [[built-vars result] @last-build]
          (if (= vars built-vars)
            result
            (let [result (build vars)]
              (reset! last-build [vars result])
              result)))))))

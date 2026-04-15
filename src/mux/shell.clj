(ns mux.shell
  "Minimal shell helpers used by mux backends.
   Replaces the common.clj dependency from agentic-stuff."
  (:require [babashka.process :as p]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(defn sh
  "Run command, return trimmed stdout. Throws on non-zero exit."
  [& args]
  (let [result (apply p/sh args)]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "Command failed (exit " (:exit result) "): " (pr-str args))
                      {:exit (:exit result) :cmd args
                       :out (:out result) :err (:err result)})))
    (str/trim (:out result))))

(defn sh?
  "Run command, return trimmed stdout or nil on failure."
  [& args]
  (try (apply sh args) (catch Exception _ nil)))

(defn md5-hex
  "Full MD5 hex digest of a string."
  [s]
  (let [digest (MessageDigest/getInstance "MD5")
        bytes  (.digest digest (.getBytes (str s) "UTF-8"))]
    (apply str (map #(format "%02x" %) bytes))))

(defn md5-short
  "First 6 hex chars of MD5 digest."
  [s]
  (subs (md5-hex s) 0 6))

(ns mux.shell
  "Minimal shell helpers used by mux backends.
   Replaces the common.clj dependency from agentic-stuff."
  (:require [babashka.process :as p]
            [clojure.string :as str])
  (:import [java.security MessageDigest]))

(defn normalize-result [result cmd]
  {:exit (:exit result)
   :cmd cmd
   :out (:out result)
   :err (:err result)})

(defn sh
  "Run command, return trimmed stdout. Throws on non-zero exit."
  [& args]
  (let [result (apply p/sh {:out :string :err :string} args)
        data (normalize-result result args)]
    (when-not (zero? (:exit result))
      (throw (ex-info (str "Command failed (exit " (:exit result) "): " (str/join " " args)) data)))
    (str/trim (:out result))))

(defn sh?
  "Run command, return trimmed stdout or nil on failure."
  [& args]
  (try (apply sh args) (catch Exception _ nil)))

(defn spawn
  "Spawn non-blocking process handle using babashka.process/process."
  [& args]
  (apply p/process {:out :string :err :string} args))

(defn alive?
  "True if process is alive."
  [proc]
  (p/alive? proc))

(defn wait
  "Wait for process completion. Optional {:timeout-ms N} returns timeout marker.
   Returns process result map with normalized keys." 
  ([proc] (wait proc nil))
  ([proc {:keys [timeout-ms]}]
   (if timeout-ms
     (let [res (deref proc timeout-ms ::timeout)]
       (if (= ::timeout res)
         {:status :timeout
          :timeout-ms timeout-ms
          :cmd (:cmd proc)
          :exit nil
          :out nil
          :err nil}
         (normalize-result res (:cmd res))))
     (normalize-result @proc (:cmd @proc)))))

(defn run!
  "Spawn and wait. Returns trimmed stdout, throws on non-zero exit with normalized data."
  [& args]
  (let [res (wait (apply spawn args))]
    (when-not (zero? (:exit res))
      (throw (ex-info (str "Command failed (exit " (:exit res) "): " (str/join " " args)) res)))
    (str/trim (:out res))))

(defn kill!
  "Destroy process tree."
  [proc]
  (p/destroy-tree proc))

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

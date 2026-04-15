#!/usr/bin/env bb

(ns unit.runner
  (:require [clojure.test :as t]
            unit.shell-test
            unit.protocol-test
            unit.cmux-test
            unit.tmux-test
            unit.runner-test
            unit.preflight-test))

(defn -main [& _]
  (let [{:keys [test fail error]} (t/run-tests
                                    'unit.shell-test
                                    'unit.protocol-test
                                    'unit.cmux-test
                                    'unit.tmux-test
                                    'unit.runner-test
                                    'unit.preflight-test)]
    (if (zero? test)
      (do
        (println "Warning: No tests found")
        (System/exit 0))
      (System/exit (+ fail error)))))

(when (= *file* (System/getProperty "babashka.file"))
  (apply -main *command-line-args*))

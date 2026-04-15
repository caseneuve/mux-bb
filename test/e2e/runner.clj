(require '[end2edn.core :as e2e])

(let [exit-code (e2e/run-file "test/e2e")]
  (System/exit exit-code))

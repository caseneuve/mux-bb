(ns unit.preflight-test
  (:require [mux.runner.preflight :as sut]
            [clojure.test :refer [deftest is testing]]))

;; ---------------------------------------------------------------------------
;; parse-probe-marker (pure)
;; ---------------------------------------------------------------------------

(deftest parse-probe-marker-test
  (testing "extracts result after marker prefix"
    (is (= "VENV:/home/user/.venvs/pa39:CWD:/home/user/PA"
           (sut/parse-probe-marker
             "PROBE_123"
             "some noise\nPROBE_123:VENV:/home/user/.venvs/pa39:CWD:/home/user/PA\nprompt"))))

  (testing "returns nil when marker not found"
    (is (nil? (sut/parse-probe-marker "PROBE_789" "no matching line here"))))

  (testing "returns nil for empty input"
    (is (nil? (sut/parse-probe-marker "PROBE_X" "")))
    (is (nil? (sut/parse-probe-marker "PROBE_X" nil))))

  (testing "uses last occurrence if multiple markers present"
    (is (= "new-result"
           (sut/parse-probe-marker
             "PF"
             "PF:old-result\nPF:new-result"))))

  (testing "returns empty string when marker has no value"
    (is (= "" (sut/parse-probe-marker "PF" "PF:"))))

  (testing "handles colon in result value"
    (is (= "key:val:extra"
           (sut/parse-probe-marker "MK" "MK:key:val:extra")))))

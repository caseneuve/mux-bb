(ns unit.protocol-test
  (:require [mux.protocol :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest detect-mux-test
  (testing "detects cmux from CMUX_SOCKET_PATH"
    (is (= :cmux (sut/detect-mux {"CMUX_SOCKET_PATH" "/tmp/cmux.sock"}))))

  (testing "detects tmux from TMUX"
    (is (= :tmux (sut/detect-mux {"TMUX" "/tmp/tmux-1001/default,12345,0"}))))

  (testing "cmux takes priority over tmux"
    (is (= :cmux (sut/detect-mux {"CMUX_SOCKET_PATH" "/tmp/cmux.sock"
                                   "TMUX" "/tmp/tmux-1001/default,12345,0"}))))

  (testing "falls back to tmux when neither set"
    (is (= :tmux (sut/detect-mux {}))))

  (testing "empty string values treated as absent"
    (is (= :tmux (sut/detect-mux {"CMUX_SOCKET_PATH" ""
                                   "TMUX" ""}))))

  (testing "nil values treated as absent"
    (is (= :tmux (sut/detect-mux {"CMUX_SOCKET_PATH" nil}))))

  (testing "make-backend throws helpful error on unknown type"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo #"Unknown mux backend"
          (sut/make-backend :zellij {})))))

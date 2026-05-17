(ns unit.shell-test
  (:require [mux.shell :as sut]
            [clojure.test :refer [deftest is testing]]))

(deftest md5-hex-test
  (testing "produces 32-char hex string"
    (let [result (sut/md5-hex "hello")]
      (is (= 32 (count result)))
      (is (re-matches #"[0-9a-f]+" result))))

  (testing "known digest"
    (is (= "5d41402abc4b2a76b9719d911017c592"
           (sut/md5-hex "hello"))))

  (testing "empty string has valid digest"
    (is (= "d41d8cd98f00b204e9800998ecf8427e"
           (sut/md5-hex ""))))

  (testing "nil input does not throw"
    (is (string? (sut/md5-hex nil)))))

(deftest md5-short-test
  (testing "returns first 6 chars of md5-hex"
    (is (= "5d4140" (sut/md5-short "hello"))))

  (testing "always 6 chars"
    (is (= 6 (count (sut/md5-short "anything"))))))

(deftest sh-test
  (testing "returns trimmed stdout"
    (is (= "hello" (sut/sh "echo" "hello"))))

  (testing "trims trailing newline"
    (is (= "hi" (sut/sh "printf" "hi\n"))))

  (testing "throws on non-zero exit"
    (is (thrown? Exception (sut/sh "false")))))

(deftest sh?-test
  (testing "returns trimmed stdout on success"
    (is (= "hello" (sut/sh? "echo" "hello"))))

  (testing "returns nil on failure"
    (is (nil? (sut/sh? "false"))))

  (testing "returns nil on missing command"
    (is (nil? (sut/sh? "nonexistent-command-xyz")))))

(deftest process-native-helpers-test
  (testing "spawn returns a live process handle"
    (let [proc (sut/spawn "sleep" "0.1")]
      (is (sut/alive? proc))
      (sut/wait proc)))

  (testing "run! waits and returns trimmed stdout"
    (is (= "hello" (sut/run! "echo" "hello"))))

  (testing "wait throws with normalized data on non-zero exit"
    (let [e (try
              (sut/run! "sh" "-c" "echo boom >&2; exit 7")
              (catch Exception ex ex))
          d (ex-data e)]
      (is (= 7 (:exit d)))
      (is (contains? d :cmd))
      (is (contains? d :err))))

  (testing "wait with timeout returns tagged timeout map"
    (let [proc (sut/spawn "sleep" "2")
          out (sut/wait proc {:timeout-ms 50})]
      (is (= :timeout (:status out)))
      (is (contains? out :cmd))
      (sut/kill! proc)
      (Thread/sleep 50)
      (is (false? (sut/alive? proc))))))

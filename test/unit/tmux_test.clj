(ns unit.tmux-test
  (:require [mux.tmux :as sut]
            [mux.shell :as sh]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

;; ---------------------------------------------------------------------------
;; derive-session-info
;; ---------------------------------------------------------------------------

(deftest derive-session-info-test
  (testing "builds socket path and session name from project + branch"
    (let [info (sut/derive-session-info "myapp" "main")]
      (is (= "myapp" (:project info)))
      (is (= "main" (:branch info)))
      (is (string? (:hash info)))
      (is (= 6 (count (:hash info))))
      (is (str/starts-with? (:sock info) "/tmp/claude-myapp-"))
      (is (str/starts-with? (:session info) "myapp-"))))

  (testing "different branches produce different hashes"
    (let [a (sut/derive-session-info "app" "main")
          b (sut/derive-session-info "app" "develop")]
      (is (not= (:hash a) (:hash b)))
      (is (not= (:sock a) (:sock b)))
      (is (not= (:session a) (:session b)))))

  (testing "same inputs produce same outputs (deterministic)"
    (let [a (sut/derive-session-info "app" "feature/x")
          b (sut/derive-session-info "app" "feature/x")]
      (is (= a b))))

  (testing "hash is md5-short of branch"
    (let [info (sut/derive-session-info "app" "main")]
      (is (= (sh/md5-short "main") (:hash info))))))

;; ---------------------------------------------------------------------------
;; build-target
;; ---------------------------------------------------------------------------

(deftest build-target-test
  (testing "builds session:window target"
    (is (= "my-session:my-window"
           (sut/build-target "my-session" "my-window"))))

  (testing "handles special characters in names"
    (is (= "app-abc123:test-runner"
           (sut/build-target "app-abc123" "test-runner")))))

;; ---------------------------------------------------------------------------
;; make-backend structure
;; ---------------------------------------------------------------------------

(deftest make-backend-test
  (testing "returns map with required protocol keys"
    (let [backend (sut/make-backend {:sock "/tmp/test.sock" :session "test-sess"})]
      (is (fn? (:new-window! backend)))
      (is (fn? (:send! backend)))
      (is (fn? (:capture! backend)))
      (is (fn? (:list! backend)))
      (is (= "tmux" (:name backend)))))

  (testing "stores context"
    (let [ctx {:sock "/tmp/test.sock" :session "test-sess"}
          backend (sut/make-backend ctx)]
      (is (= ctx (:ctx backend))))))

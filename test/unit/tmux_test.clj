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
      (is (= (sh/md5-short "main") (:hash info)))))

  (testing "sanitizes slashes in project name"
    (let [info (sut/derive-session-info "my/project" "main")]
      (is (not (str/includes? (:session info) "/")))
      (is (not (str/includes? (last (str/split (:sock info) #"/")) "/")))))

  (testing "sanitizes dots and colons in project name"
    (let [info (sut/derive-session-info "my.project:v2" "main")]
      (is (not (re-find #"[.:]" (:session info)))))))

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
      (is (= ctx (:ctx backend)))))

  (testing "list! returns vector not nil when no windows exist"
    ;; Structural: the fn should always return a vector, even if tmux? returns nil
    ;; Can't test with real tmux here, but verify the fn exists and returns a vector
    ;; when given a fake sock (tmux? returns nil)
    (let [backend (sut/make-backend {:sock "/tmp/nonexistent.sock" :session "none"})]
      (is (vector? ((:list! backend)))))))

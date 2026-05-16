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
      (is (= "/tmp/mux.sock" (:sock info)))
      (is (str/starts-with? (:session info) "myapp-"))))

  (testing "different branches produce different sessions but same socket"
    (let [a (sut/derive-session-info "app" "main")
          b (sut/derive-session-info "app" "develop")]
      (is (not= (:hash a) (:hash b)))
      (is (= (:sock a) (:sock b)))
      (is (not= (:session a) (:session b)))))

  (testing "same inputs produce same outputs (deterministic)"
    (let [a (sut/derive-session-info "app" "feature/x")
          b (sut/derive-session-info "app" "feature/x")]
      (is (= a b))))

  (testing "hash is md5-short of branch"
    (let [info (sut/derive-session-info "app" "main")]
      (is (= (sh/md5-short "main") (:hash info)))))

  (testing "default socket is /tmp/mux.sock"
    (let [info (sut/derive-session-info "app" "main")]
      (is (= "/tmp/mux.sock" (:sock info)))))

  (testing "custom socket override via opts"
    (let [info (sut/derive-session-info "app" "main" {:sock "/tmp/custom.sock"})]
      (is (= "/tmp/custom.sock" (:sock info)))))

  (testing "sanitizes slashes in project name"
    (let [info (sut/derive-session-info "my/project" "main")]
      (is (not (str/includes? (:session info) "/")))))

  (testing "sanitizes dots and colons in project name"
    (let [info (sut/derive-session-info "my.project:v2" "main")]
      (is (not (re-find #"[.:]" (:session info))))))

  (testing "degenerate project name falls back to 'unnamed'"
    (let [info (sut/derive-session-info "///" "main")]
      (is (str/starts-with? (:session info) "unnamed-"))))

  (testing "nil project falls back to 'unnamed'"
    (let [info (sut/derive-session-info nil "main")]
      (is (str/starts-with? (:session info) "unnamed-")))))

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

(deftest direction-and-spawn-args-test
  (testing "maps logical direction to tmux flags"
    (is (= ["-h"] (sut/direction->flags :right)))
    (is (= ["-h" "-b"] (sut/direction->flags :left)))
    (is (= ["-v"] (sut/direction->flags :below)))
    (is (= ["-v" "-b"] (sut/direction->flags :above))))

  (testing "throws on invalid direction"
    (is (= :invalid-direction
           (:cause (ex-data (try (sut/direction->flags :diagonal)
                                 (catch Exception e e)))))))

  (testing "builds split-window args"
    (is (= ["split-window" "-P" "-F" "#{session_name}|#{window_name}|#{pane_id}|#{session_name}:#{window_name}.#{pane_index}"
            "-h" "-l" "30%" "-t" "s:w.0" "-c" "/tmp" "echo hi"]
           (sut/build-spawn-pane-args {:direction :right
                                       :size "30%"
                                       :target "s:w.0"
                                       :cwd "/tmp"
                                       :command "echo hi"})))))

(deftest make-backend-test
  (testing "returns map with required protocol keys"
    (let [backend (sut/make-backend {:sock "/tmp/test.sock" :session "test-sess"})]
      (is (fn? (:new-window! backend)))
      (is (fn? (:send! backend)))
      (is (fn? (:capture! backend)))
      (is (fn? (:list! backend)))
      (is (fn? (:spawn-pane! backend)))
      (is (= "tmux" (:name backend)))))

  (testing "stores context"
    (let [ctx {:sock "/tmp/test.sock" :session "test-sess"}
          backend (sut/make-backend ctx)]
      (is (= ctx (:ctx backend)))))

  (testing "list! returns vector not nil when no windows exist"
    ;; Structural: the fn should always return a vector, even if tmux? returns nil
    (let [backend (sut/make-backend {:sock "/tmp/nonexistent.sock" :session "none"})]
      (is (vector? ((:list! backend))))))

  (testing "spawn-pane! returns metadata from tmux format output"
    (let [backend (sut/make-backend {:sock "/tmp/test.sock" :session "sess"})]
      (with-redefs [sut/tmux? (fn [& _] "sess:main.0")
                    sut/tmux! (fn [& _] "sess|main|%7|sess:main.1")]
        (is (= {:session "sess"
                :window "main"
                :pane-id "%7"
                :target "sess:main.1"
                :launch-command "echo OK"}
               ((:spawn-pane! backend) {:command "echo OK"}))))))

  (testing "spawn-pane! raises invalid-target when no explicit/current target"
    (let [backend (sut/make-backend {:sock "/tmp/test.sock" :session "sess"})]
      (with-redefs [sut/tmux? (fn [& _] nil)]
        (let [e (try ((:spawn-pane! backend) {:command "echo hi"})
                     (catch Exception ex ex))]
          (is (= :invalid-target (:cause (ex-data e)))))))))

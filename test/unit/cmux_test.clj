(ns unit.cmux-test
  (:require [mux.cmux :as sut]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

;; ---------------------------------------------------------------------------
;; escape-send-text
;; ---------------------------------------------------------------------------

(deftest escape-send-text-test
  (testing "newlines become \\n"
    (is (= "echo hello\\n" (sut/escape-send-text "echo hello\n"))))

  (testing "tabs become \\t"
    (is (= "a\\tb" (sut/escape-send-text "a\tb"))))

  (testing "plain text unchanged"
    (is (= "echo hi" (sut/escape-send-text "echo hi"))))

  (testing "mixed newlines and tabs"
    (is (= "a\\nb\\tc\\n" (sut/escape-send-text "a\nb\tc\n")))))

;; ---------------------------------------------------------------------------
;; build-cmux-args
;; ---------------------------------------------------------------------------

(deftest build-cmux-args-send-test
  (testing "send with workspace"
    (is (= ["send" "--workspace" "ws-123" "--" "echo hello\\n"]
           (sut/build-cmux-args :send {:workspace "ws-123" :text "echo hello\n"}))))

  (testing "send without workspace"
    (is (= ["send" "--" "echo hi\\n"]
           (sut/build-cmux-args :send {:text "echo hi\n"})))))

(deftest build-cmux-args-capture-test
  (testing "capture with workspace"
    (is (= ["capture-pane" "--workspace" "ws-123" "--scrollback" "--lines" "1000"]
           (sut/build-cmux-args :capture {:workspace "ws-123" :lines 1000}))))

  (testing "capture without workspace"
    (is (= ["capture-pane" "--scrollback" "--lines" "1000"]
           (sut/build-cmux-args :capture {:lines 1000})))))

(deftest build-cmux-args-new-workspace-test
  (testing "with name and cwd"
    (is (= ["new-workspace" "--name" "test-win" "--cwd" "/tmp/worktree"]
           (sut/build-cmux-args :new-workspace {:name "test-win" :cwd "/tmp/worktree"}))))

  (testing "with name only"
    (is (= ["new-workspace" "--name" "test-win"]
           (sut/build-cmux-args :new-workspace {:name "test-win"}))))

  (testing "with cwd only"
    (is (= ["new-workspace" "--cwd" "/tmp"]
           (sut/build-cmux-args :new-workspace {:cwd "/tmp"}))))

  (testing "empty params"
    (is (= ["new-workspace"]
           (sut/build-cmux-args :new-workspace {})))))

(deftest build-cmux-args-list-workspaces-test
  (testing "list-workspaces"
    (is (= ["list-workspaces"]
           (sut/build-cmux-args :list-workspaces {})))))

(deftest build-cmux-args-wait-for-test
  (testing "wait-for signal"
    (is (= ["wait-for" "-S" "my-signal"]
           (sut/build-cmux-args :wait-for {:signal? true :name "my-signal"}))))

  (testing "wait-for with timeout"
    (is (= ["wait-for" "my-signal" "--timeout" "300"]
           (sut/build-cmux-args :wait-for {:name "my-signal" :timeout 300}))))

  (testing "wait-for without signal flag"
    (is (= ["wait-for" "done"]
           (sut/build-cmux-args :wait-for {:name "done"})))))

(deftest build-cmux-args-notify-test
  (testing "full notify"
    (is (= ["notify" "--workspace" "ws-1" "--title" "Done" "--body" "exit 0"]
           (sut/build-cmux-args :notify {:workspace "ws-1" :title "Done" :body "exit 0"}))))

  (testing "notify without body"
    (is (= ["notify" "--workspace" "ws-1" "--title" "Done"]
           (sut/build-cmux-args :notify {:workspace "ws-1" :title "Done"}))))

  (testing "notify without workspace"
    (is (= ["notify" "--title" "Done" "--body" "ok"]
           (sut/build-cmux-args :notify {:title "Done" :body "ok"})))))

(deftest build-cmux-args-set-description-test
  (testing "set-description"
    (is (= ["workspace-action" "--action" "set-description"
            "--workspace" "ws-1" "--description" "Running: pytest"]
           (sut/build-cmux-args :set-description {:workspace "ws-1"
                                                   :description "Running: pytest"})))))

(deftest build-cmux-args-set-color-test
  (testing "set-color"
    (is (= ["workspace-action" "--action" "set-color"
            "--workspace" "ws-1" "--color" "#22C55E"]
           (sut/build-cmux-args :set-color {:workspace "ws-1" :color "#22C55E"})))))

;; ---------------------------------------------------------------------------
;; parse-workspaces
;; ---------------------------------------------------------------------------

(deftest parse-workspaces-test
  (testing "parses workspace list output"
    (let [output "* workspace:1  Terminal 1  [selected]\n  workspace:7  Terminal 2"]
      (is (= [{:id "workspace:1" :name "Terminal 1" :selected? true}
              {:id "workspace:7" :name "Terminal 2" :selected? false}]
             (sut/parse-workspaces output)))))

  (testing "handles workspace with custom name"
    (let [output "  workspace:3  mywin"]
      (is (= [{:id "workspace:3" :name "mywin" :selected? false}]
             (sut/parse-workspaces output)))))

  (testing "handles first line trimmed by sh (no leading spaces)"
    (let [output "workspace:22  mywin\n  workspace:1  Terminal 1\n* workspace:20  stuff  [selected]"]
      (is (= [{:id "workspace:22" :name "mywin" :selected? false}
              {:id "workspace:1" :name "Terminal 1" :selected? false}
              {:id "workspace:20" :name "stuff" :selected? true}]
             (sut/parse-workspaces output)))))

  (testing "single workspace"
    (is (= [{:id "workspace:1" :name "main" :selected? true}]
           (sut/parse-workspaces "* workspace:1  main  [selected]"))))

  (testing "empty output"
    (is (= [] (sut/parse-workspaces "")))
    (is (= [] (sut/parse-workspaces nil))))

  (testing "blank output"
    (is (= [] (sut/parse-workspaces "   \n  ")))))

;; ---------------------------------------------------------------------------
;; make-backend structure
;; ---------------------------------------------------------------------------

(deftest make-backend-test
  (testing "returns map with required protocol keys"
    (let [backend (sut/make-backend {:cmux-bin "/usr/bin/cmux"})]
      (is (fn? (:new-window! backend)))
      (is (fn? (:send! backend)))
      (is (fn? (:capture! backend)))
      (is (fn? (:list! backend)))
      (is (= "cmux" (:name backend)))))

  (testing "returns map with extended protocol keys"
    (let [backend (sut/make-backend {:cmux-bin "/usr/bin/cmux"})]
      (is (fn? (:wait-for! backend)))
      (is (fn? (:signal-cmd backend)))
      (is (fn? (:notify! backend)))
      (is (fn? (:set-description! backend)))
      (is (fn? (:set-color! backend)))))

  (testing "send! throws on unregistered window"
    (let [backend (sut/make-backend {:cmux-bin "/usr/bin/cmux"})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not registered"
            ((:send! backend) "nonexistent" "echo hi")))))

  (testing "capture! throws on unregistered window"
    (let [backend (sut/make-backend {:cmux-bin "/usr/bin/cmux"})]
      (is (thrown-with-msg? clojure.lang.ExceptionInfo #"not registered"
            ((:capture! backend) "nonexistent")))))

  (testing "list! returns empty initially"
    (let [backend (sut/make-backend {:cmux-bin "/usr/bin/cmux"})]
      (is (empty? ((:list! backend))))))

  (testing "signal-cmd returns shell command string"
    (let [backend (sut/make-backend {:cmux-bin "/usr/bin/cmux"})]
      (is (= "/usr/bin/cmux wait-for -S my-signal"
             ((:signal-cmd backend) "my-signal"))))))

;; ---------------------------------------------------------------------------
;; OK prefix stripping (used in new-window! result parsing)
;; ---------------------------------------------------------------------------

(deftest parse-new-workspace-output-test
  (testing "strips OK prefix"
    (is (= "927C52A8-D9AE-4AC4-99D3-21860D8AC443"
           (str/replace-first "OK 927C52A8-D9AE-4AC4-99D3-21860D8AC443" #"^OK " ""))))

  (testing "handles trailing whitespace"
    (is (= "ABC-123"
           (str/replace-first (str/trim "OK ABC-123  \n") #"^OK " "")))))

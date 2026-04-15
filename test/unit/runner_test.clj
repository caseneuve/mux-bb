(ns unit.runner-test
  (:require [mux.runner :as sut]
            [clojure.string :as str]
            [clojure.test :refer [deftest is testing]]))

;; ---------------------------------------------------------------------------
;; make-marker
;; ---------------------------------------------------------------------------

(deftest make-marker-test
  (testing "produces MUXRUN_ prefixed string"
    (is (= "MUXRUN_1000_42" (sut/make-marker 1000 42))))

  (testing "different inputs produce different markers"
    (is (not= (sut/make-marker 1 2) (sut/make-marker 3 4))))

  (testing "components are separated by underscores"
    (let [m (sut/make-marker 999 77)]
      (is (= ["MUXRUN" "999" "77"] (clojure.string/split m #"_"))))))

;; ---------------------------------------------------------------------------
;; wrap-with-markers
;; ---------------------------------------------------------------------------

(deftest wrap-with-markers-test
  (testing "wraps command with echo start and end markers"
    (is (= "echo MK_START; echo hi; echo MK_END:$?"
           (sut/wrap-with-markers "echo hi" "MK"))))

  (testing "marker suffixes are _START and _END"
    (let [wrapped (sut/wrap-with-markers "ls" "MUXRUN_123_456")]
      (is (clojure.string/includes? wrapped "MUXRUN_123_456_START"))
      (is (clojure.string/includes? wrapped "MUXRUN_123_456_END:$?"))))

  (testing "preserves command as-is in the middle"
    (let [cmd "cd /tmp && make test"]
      (is (clojure.string/includes? (sut/wrap-with-markers cmd "M") cmd)))))

;; ---------------------------------------------------------------------------
;; extract-output
;; ---------------------------------------------------------------------------

(deftest extract-output-test
  (testing "extracts output between markers"
    (let [raw (str "some prompt\n"
                   "MARKER_START\n"
                   "line 1\n"
                   "line 2\n"
                   "MARKER_END:0\n"
                   "next prompt")]
      (is (= {:output "line 1\nline 2" :exit-code 0}
             (sut/extract-output raw "MARKER_START" "MARKER_END")))))

  (testing "captures non-zero exit code"
    (let [raw "MARKER_START\nfailed!\nMARKER_END:1\n"]
      (is (= 1 (:exit-code (sut/extract-output raw "MARKER_START" "MARKER_END"))))))

  (testing "empty output (markers adjacent)"
    (let [raw "MARKER_START\nMARKER_END:0\n"]
      (is (= {:output "" :exit-code 0}
             (sut/extract-output raw "MARKER_START" "MARKER_END")))))

  (testing "returns nil when start marker missing"
    (is (nil? (sut/extract-output "no markers here\nMARKER_END:0"
                                  "MARKER_START" "MARKER_END"))))

  (testing "returns nil when end marker missing"
    (is (nil? (sut/extract-output "MARKER_START\nstuff"
                                  "MARKER_START" "MARKER_END"))))

  (testing "uses LAST occurrence of markers (handles scrollback)"
    (let [raw (str "MARKER_START\n"
                   "old output\n"
                   "MARKER_END:2\n"
                   "prompt\n"
                   "MARKER_START\n"
                   "new output\n"
                   "MARKER_END:0\n")]
      (is (= {:output "new output" :exit-code 0}
             (sut/extract-output raw "MARKER_START" "MARKER_END")))))

  (testing "handles multi-digit exit codes"
    (let [raw "MARKER_START\ncrash\nMARKER_END:124\n"]
      (is (= 124 (:exit-code (sut/extract-output raw "MARKER_START" "MARKER_END")))))))

;; ---------------------------------------------------------------------------
;; parse-args
;; ---------------------------------------------------------------------------

(deftest parse-args-test
  (testing "positional args: window and command"
    (is (= {:timeout 300 :window "unit" :command "echo hi"}
           (sut/parse-args ["unit" "echo hi"]))))

  (testing "named args"
    (is (= {:timeout 600 :window "w" :command "cmd"
            :cd "/tmp" :sock "/tmp/s.sock" :session "s-123"}
           (sut/parse-args ["w" "cmd"
                            "--timeout" "600"
                            "--cd" "/tmp"
                            "--sock" "/tmp/s.sock"
                            "--session" "s-123"]))))

  (testing "defaults timeout to 300"
    (is (= 300 (:timeout (sut/parse-args ["w" "c"])))))

  (testing "window only — no command"
    (is (nil? (:command (sut/parse-args ["unit"])))))

  (testing "empty args"
    (let [opts (sut/parse-args [])]
      (is (nil? (:window opts)))
      (is (nil? (:command opts))))))

;; ---------------------------------------------------------------------------
;; Workspace UX colors
;; ---------------------------------------------------------------------------

(deftest color-constants-test
  (testing "color constants are valid hex strings"
    (is (re-matches #"#[0-9A-Fa-f]{6}" sut/color-running))
    (is (re-matches #"#[0-9A-Fa-f]{6}" sut/color-passed))
    (is (re-matches #"#[0-9A-Fa-f]{6}" sut/color-failed)))

  (testing "colors are distinct"
    (is (not= sut/color-running sut/color-passed))
    (is (not= sut/color-running sut/color-failed))
    (is (not= sut/color-passed sut/color-failed))))

;; ---------------------------------------------------------------------------
;; run-cmd! with mock backend
;; ---------------------------------------------------------------------------

(defn- make-mock-backend
  "Create a mock backend that records calls and returns pre-set capture output."
  [capture-output & [{:keys [has-wait-for?]}]]
  (let [calls (atom [])]
    {:calls calls
     :backend
     (cond-> {:name "mock"
              :send!    (fn [win text] (swap! calls conj [:send! win text]))
              :capture! (fn [win]
                          (swap! calls conj [:capture! win])
                          capture-output)
              :list!    (fn [] [])}
       has-wait-for?
       (assoc :wait-for! (fn [signal timeout]
                           (swap! calls conj [:wait-for! signal timeout]))))}))

(deftest run-cmd!-mock-test
  (testing "sends wrapped command and extracts output"
    (let [marker-str  "MUXRUN_1000_42"
          raw-capture (str "MUXRUN_1000_42_START\nhello world\nMUXRUN_1000_42_END:0\n")
          {:keys [calls backend]} (make-mock-backend raw-capture)]
      (with-redefs [sut/make-marker (fn [_ _] "MUXRUN_1000_42")]
        (let [result (sut/run-cmd! backend {:window "test" :command "echo hello" :timeout 5})]
          (is (= "hello world" (:output result)))
          (is (= 0 (:exit-code result)))
          ;; verify send! was called
          (is (some #(= :send! (first %)) @calls))
          ;; verify capture! was called
          (is (some #(= :capture! (first %)) @calls))))))

  (testing "sends cd before command when :cd is set"
    (let [raw-capture "MUXRUN_1000_42_START\nok\nMUXRUN_1000_42_END:0\n"
          {:keys [calls backend]} (make-mock-backend raw-capture)]
      (with-redefs [sut/make-marker (fn [_ _] "MUXRUN_1000_42")]
        (sut/run-cmd! backend {:window "w" :command "ls" :timeout 5 :cd "/tmp"})
        (let [sends (filter #(= :send! (first %)) @calls)]
          ;; first send should be the cd command
          (is (str/includes? (nth (first sends) 2) "cd /tmp"))))))

  (testing "uses cmux native wait-for! when available"
    (let [raw-capture "MUXRUN_1000_42_START\nresult\nMUXRUN_1000_42_END:0\n"
          {:keys [calls backend]} (make-mock-backend raw-capture {:has-wait-for? true})]
      (with-redefs [sut/make-marker (fn [_ _] "MUXRUN_1000_42")]
        (sut/run-cmd! backend {:window "w" :command "ls" :timeout 10})
        ;; verify wait-for! was called (not polling)
        (is (some #(= :wait-for! (first %)) @calls)))))

  (testing "returns non-zero exit code"
    (let [raw-capture "MUXRUN_1000_42_START\nerror msg\nMUXRUN_1000_42_END:42\n"
          {:keys [calls backend]} (make-mock-backend raw-capture)]
      (with-redefs [sut/make-marker (fn [_ _] "MUXRUN_1000_42")]
        (let [result (sut/run-cmd! backend {:window "w" :command "fail" :timeout 5})]
          (is (= 42 (:exit-code result)))
          (is (= "error msg" (:output result)))))))

  (testing "throws when markers not found in capture"
    (let [;; end marker present so wait completes, but no start marker
          raw "no start here\nMUXRUN_1000_42_END:0\n"
          {:keys [backend]} (make-mock-backend raw)]
      (with-redefs [sut/make-marker (fn [_ _] "MUXRUN_1000_42")]
        (is (thrown-with-msg? clojure.lang.ExceptionInfo #"markers"
              (sut/run-cmd! backend {:window "w" :command "cmd" :timeout 5})))))))

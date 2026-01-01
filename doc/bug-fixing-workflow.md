# Bug Fixing Workflow

This document describes the recommended workflow for fixing bugs in the Alert Scout codebase, specifically designed for working with Claude Code.

## TDD Approach for Bug Fixes

When a bug is reported or discovered, follow this strict Test-Driven Development (TDD) workflow:

### 1. Reproduce the Bug

First, create a minimal reproduction case:

```clojure
;; Try to reproduce the bug in the REPL
(require '[alert-scout.excerpts :as excerpts])

;; Use the exact scenario that triggers the bug
(let [text "..."
      position {:start 76 :end 81 :term "rails"}]
  (excerpts/extract-excerpt text position 50))
;; => StringIndexOutOfBoundsException: Range [158, 19) out of bounds
```

Document:
- The exact error message
- The input data that triggers it
- The expected behavior
- The actual behavior

### 2. Write a Failing Test

Before fixing anything, write a test that captures the bug:

```clojure
(testing "Word boundary crossing with long unbroken text"
  ;; This test verifies the bugfix for StringIndexOutOfBoundsException
  ;; when word boundaries cross (start > end)
  (let [text (str "VeryLongWordWithoutAnySpaces..."
                  "rails"
                  "AnotherVeryLongWordWithoutSpaces...")
        position {:start 76 :end 81 :term "rails"}
        result (excerpts/extract-excerpt text position 50)]
    ;; Test should fail here with the bug present
    (is (some? result))
    (is (< (:start result) (:end result)))))
```

**Key principles:**
- Add a comment explaining what bug this test verifies
- Make the test as minimal as possible while still catching the bug
- Use descriptive test names that reference the bug scenario

### 3. Verify the Test Fails

**Critical step:** Run the test to ensure it actually catches the bug:

```bash
lein test alert-scout.excerpts-test
```

Expected output:
```
ERROR in (test-extract-excerpt) (excerpts.clj:69)
java.lang.StringIndexOutOfBoundsException: Range [158, 19) out of bounds
```

**If the test passes when it should fail:**
- The test doesn't actually reproduce the bug
- Refine the test case to trigger the bug condition
- The bug may have already been fixed elsewhere

### 4. Fix the Bug

Now implement the fix:

```clojure
;; Before (buggy code)
excerpt-start (find-word-boundary text desired-start :after)
excerpt-end (find-word-boundary text desired-end :before)
excerpt-text (subs text excerpt-start excerpt-end)  ;; Can crash!

;; After (with fix)
excerpt-start (find-word-boundary text desired-start :after)
excerpt-end (find-word-boundary text desired-end :before)

;; Safety check: if word boundaries crossed, fall back to desired positions
[final-start final-end] (if (>= excerpt-start excerpt-end)
                           [desired-start desired-end]
                           [excerpt-start excerpt-end])

excerpt-text (subs text final-start final-end)  ;; Safe!
```

### 5. Verify the Test Passes

Run the test again to confirm the fix works:

```bash
lein test alert-scout.excerpts-test
```

Expected output:
```
Ran 10 tests containing 74 assertions.
0 failures, 0 errors.
```

### 6. Run Full Test Suite

Ensure the fix doesn't break anything else:

```bash
lein test
```

All tests should pass. If any tests fail:
- The fix introduced a regression
- Review the failing tests to understand what broke
- Refine the fix to handle both the bug and the existing behavior

### 7. Document the Bug

Add documentation in multiple places:

**In the test itself (inline comment):**
```clojure
(testing "Word boundary crossing with long unbroken text"
  ;; This test verifies the bugfix for StringIndexOutOfBoundsException
  ;; when word boundaries cross (start > end)
  ...)
```

**In the code (if the fix is non-obvious):**
```clojure
;; Safety check: if word boundaries crossed, fall back to desired positions
[final-start final-end] (if (>= excerpt-start excerpt-end)
                           [desired-start desired-end]
                           [excerpt-start excerpt-end])
```

**In git commit message:**
```
Fix StringIndexOutOfBoundsException in excerpt extraction

When word boundaries are detected in long unbroken text (no spaces),
the forward search from desired-start could land after the backward
search from desired-end, causing start > end and crashing subs.

Added safety check to fall back to raw positions when boundaries cross.

Added regression test: "Word boundary crossing with long unbroken text"
```

## Additional Best Practices

### Edge Case Identification

When fixing a bug, identify related edge cases that might have similar issues:

```clojure
;; Original bug: long text without spaces
(testing "Word boundary crossing with long unbroken text" ...)

;; Related edge cases to test:
(testing "Empty text" ...)
(testing "Text with only spaces" ...)
(testing "Match at text boundaries (start/end)" ...)
(testing "Text shorter than context window" ...)
(testing "Multiple matches in long unbroken text" ...)
```

### Root Cause Analysis

Document the root cause in the test or code comments:

```clojure
;; Root cause: Word boundary detection uses bi-directional search
;; - :after from start (to skip partial words at beginning)
;; - :before from end (to skip partial words at end)
;; In pathological cases (long text without spaces), these can cross.
```

This helps future developers understand why the fix was necessary.

### Defensive Programming

After a bug fix, consider if similar patterns exist elsewhere:

```bash
# Search for similar patterns that might have the same bug
rg "subs.*excerpt-start.*excerpt-end" src/
```

Add defensive checks proactively if found.

### Performance Testing

If the fix adds overhead (like boundary checks), verify performance:

```clojure
(testing "Performance remains under 5ms threshold"
  (let [text (apply str (repeat 1000 "word "))
        position {:start 50 :end 54 :term "word"}
        start (System/nanoTime)]
    (dotimes [_ 100]
      (excerpts/extract-excerpt text position 50))
    (let [elapsed-ms (/ (- (System/nanoTime) start) 1000000.0)]
      (is (< elapsed-ms 500)))))  ;; 100 iterations in <500ms
```

### REPL-Based Verification (Optional but Recommended)

Before committing, manually verify in the REPL:

```clojure
;; Start REPL: lein repl

;; Load fixed namespace
(require '[alert-scout.excerpts :as excerpts] :reload)

;; Test the exact scenario from the bug report
(let [text "VeryLongWordWithoutAnySpaces...rails...MoreLongWords"]
  (excerpts/extract-excerpt text {:start 76 :end 81 :term "rails"} 50))
;; => {:text "...", :start 26, :end 131, :position {...}}  ;; Success!
```

This provides confidence the fix works in realistic scenarios.

## Common Pitfalls

### 1. Testing the Fix Instead of the Bug

**Wrong:**
```clojure
;; This tests the implementation, not the bug
(testing "Boundary check works"
  (is (< final-start final-end)))
```

**Right:**
```clojure
;; This tests the behavior that was broken
(testing "Long unbroken text doesn't crash"
  (let [result (excerpts/extract-excerpt long-text position 50)]
    (is (some? result))  ;; Would have been nil/crash before fix
    (is (.contains (:text result) "rails"))))
```

### 2. Overly Complex Test Cases

Keep bug reproduction minimal:

**Wrong:**
```clojure
;; Too much setup, hard to understand what triggers the bug
(testing "Complex scenario with multiple feeds and rules" ...)
```

**Right:**
```clojure
;; Minimal reproduction - exactly what's needed to trigger the bug
(testing "Word boundary crossing with long unbroken text"
  (let [text (str "LongWord" "rails" "LongWord")
        position {:start 8 :end 13 :term "rails"}]
    ...))
```

### 3. Not Verifying the Test Catches the Bug

Always run the test before fixing to confirm it fails. Otherwise:
- The test might not actually catch the bug
- You're testing the wrong thing
- The bug might already be fixed

### 4. Incomplete Fixes

Ensure the fix handles all related cases:

```clojure
;; Incomplete fix - only handles one direction
(if (> excerpt-start excerpt-end)
  [desired-start desired-end]
  [excerpt-start excerpt-end])

;; Complete fix - handles equality too
(if (>= excerpt-start excerpt-end)  ;; Note: >= not just >
  [desired-start desired-end]
  [excerpt-start excerpt-end])
```

## Checklist

Use this checklist when fixing bugs:

- [ ] Reproduced the bug manually (REPL or actual usage)
- [ ] Wrote a failing test that captures the bug
- [ ] Verified the test fails with the expected error
- [ ] Implemented the fix with minimal changes
- [ ] Verified the test now passes
- [ ] Ran full test suite - all tests pass
- [ ] Checked for similar patterns elsewhere in codebase
- [ ] Added explanatory comments in test and/or code
- [ ] Considered and tested related edge cases
- [ ] Verified no performance regression (if applicable)
- [ ] Documented root cause in commit message
- [ ] Manually tested in REPL (optional but recommended)

## Example: Complete Bug Fix Flow

```bash
# 1. User reports bug
# "StringIndexOutOfBoundsException when processing HN feed"

# 2. Reproduce in REPL
lein repl
(require '[alert-scout.core :as core])
(core/run-once)
;; => Error: Range [158, 19) out of bounds

# 3. Write failing test
# Edit test/alert_scout/excerpts_test.clj
# Add: (testing "Word boundary crossing..." ...)

# 4. Verify test fails
lein test alert-scout.excerpts-test
;; => ERROR: StringIndexOutOfBoundsException

# 5. Implement fix
# Edit src/alert_scout/excerpts.clj
# Add boundary crossing check

# 6. Verify test passes
lein test alert-scout.excerpts-test
;; => 0 failures, 0 errors

# 7. Run full suite
lein test
;; => All 50 tests pass

# 8. Verify fix in REPL
lein repl
(require '[alert-scout.core :as core] :reload-all)
(core/run-once)
;; => Success! Processes feeds without error

# 9. Commit with clear message
git add src/alert_scout/excerpts.clj test/alert_scout/excerpts_test.clj
git commit -m "Fix StringIndexOutOfBoundsException in excerpt extraction

When word boundaries are detected in long unbroken text...
Added regression test: 'Word boundary crossing with long unbroken text'
"
```

## Working with Claude Code

When asking Claude Code to fix a bug, provide:

1. **The exact error message**: Copy the full stack trace
2. **Reproduction steps**: What action triggered the bug
3. **Expected vs actual behavior**: What should happen vs what does happen
4. **Relevant context**: Which feeds, rules, or data caused it

Example request:
```
There is this error when trying to run core/run-once:
→ Checking feed: hn-frontpage (https://hnrss.org/frontpage)
Execution error (StringIndexOutOfBoundsException) at jdk.internal.util.Preconditions$1/apply (Preconditions.java:55).
Range [158, 19) out of bounds for length 312
What is the most likely source?
```

Claude will:
1. Identify the likely source
2. Write a failing test
3. Implement the fix
4. Verify all tests pass

You should then ask:
```
Are there any test cases that can verify the bugfix?
```

This ensures the fix has proper regression coverage.

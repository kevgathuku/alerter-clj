# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Alert Scout** - An RSS/Atom feed monitoring system that matches feed items against user-defined rules and generates alerts. The project is built with Clojure using Leiningen.

## Commands

### Development
```bash
# Start REPL
lein repl

# Compile and check for errors/warnings
lein compile
lein check

# Code quality checks
lein cljfmt check          # Check code formatting
lein cljfmt fix            # Auto-fix formatting issues
lein lint                  # Run clj-kondo linting
lein check-all             # Run all checks: format, lint, compile

# Run all tests
lein test

# Run specific test namespace
lein test alert-scout.matcher-test

# Run specific test
lein test :only alert-scout.matcher-test/test-match-rule-must

# Run the application
lein run
```

### Working in the REPL
```clojure
;; Reload namespaces after making changes
(require '[alert-scout.core :as core] :reload-all)
(require '[alert-scout.storage :as storage] :reload)

;; Load configuration
(def rules (storage/load-rules! "data/rules.edn"))
(def feeds (storage/load-feeds! "data/feeds.edn"))

;; Run the feed processor
(core/process-feeds! "data/checkpoints.edn" rules feeds)

;; Run with custom feeds and rules
(def custom-feeds [{:feed-id "test" :url "https://example.com/rss"}])
(core/process-feeds! "data/checkpoints.edn" rules custom-feeds)

;; Save alerts to file
(def result (core/process-feeds! "data/checkpoints.edn" rules feeds))
(storage/save-alerts! (:alerts result) "reports/alerts.md" :markdown)

;; Validate data structures (useful during development)
(require '[alert-scout.schemas :as schemas])
(schemas/valid? schemas/Feed {:feed-id "hn" :url "https://news.ycombinator.com/rss"})
(schemas/explain schemas/Feed {:feed-id ""})  ;; See why data is invalid
```

# Clojure REPL Evaluation

The command `clj-nrepl-eval` is installed on your path for evaluating Clojure code via nREPL.

**Discover nREPL servers:**

`clj-nrepl-eval --discover-ports`

**Evaluate code:**

`clj-nrepl-eval -p <port> "<clojure-code>"`

With timeout (milliseconds)

`clj-nrepl-eval -p <port> --timeout 5000 "<clojure-code>"`

The REPL session persists between evaluations - namespaces and state are maintained.
Always use `:reload` when requiring namespaces to pick up changes.

### Building
```bash
# Create standalone JAR
lein uberjar

# Run standalone JAR
java -jar target/uberjar/my-stuff-0.1.0-SNAPSHOT-standalone.jar
```

### Continuous Integration

The project uses GitHub Actions for CI/CD with two workflows:

**Main CI Pipeline** (`.github/workflows/ci.yml`):
- Runs on push to main/master branches and pull requests
- Tests against multiple JDK versions (21, 25)
- Runs all tests
- Checks for reflection warnings
- Runs code quality checks (formatting, linting)
- Builds uberjar artifact
- Uploads build artifacts (retained for 7 days)

**Quick Check** (`.github/workflows/quick-check.yml`):
- Runs on every push to any branch
- Fast feedback for development
- Single JDK version (17)
- Runs tests and compilation checks

Both workflows:
- Cache Leiningen dependencies (`~/.m2`, `~/.lein`, `target`) for faster builds
- Fail if reflection warnings are detected
- Validate data schemas during tests
- Cache key based on `project.clj` hash

**Recommended CI command:**
```bash
lein check-all && lein test && lein uberjar
```

## Architecture

### Core Components

The application follows a pipeline architecture with clear separation of concerns:

1. **Fetcher** (`alert-scout.fetcher`) - RSS/Atom feed fetching and parsing
   - Uses Rome Tools library for feed parsing
   - Takes Feed maps (`:feed-id`, `:url`) as input
   - Normalizes feed entries into a common map structure with `:feed-id`, `:item-id`, `:title`, `:link`, `:published-at`, `:content`, `:categories`
   - **Error Handling**: Gracefully handles HTTP errors (429 rate limiting, 404, 500, etc.)
     - Logs errors to stderr with specific messages
     - Returns empty list instead of crashing
     - Allows processing to continue for other feeds
     - See `test/alert_scout/fetcher_test.clj` for error scenarios

2. **Matcher** (`alert-scout.matcher`) - Rule matching engine
   - Implements boolean search logic with `must`, `should`, `must-not`, and `min-should-match` fields
   - Matching is case-insensitive and searches both title and content

3. **Storage** (`alert-scout.storage`) - Data persistence layer
   - Handles EDN file I/O for rules, feeds, and checkpoints
   - Maintains in-memory checkpoint state using atoms
   - Provides CRUD operations for feed management
   - Validates data against schemas when loading/saving

4. **Schemas** (`alert-scout.schemas`) - Data validation using Malli
   - Defines schemas for all domain objects (Feed, Rule, FeedItem, Alert)
   - Provides validation functions with clear error messages
   - Protects against invalid data at system boundaries
   - See `doc/malli-examples.md` for detailed examples

5. **Core** (`alert-scout.core`) - Main orchestration and presentation
   - Loads configuration from `data/` directory on namespace load
   - Coordinates fetching, matching, and alert emission
   - Provides colorized terminal output using ANSI codes
   - Exports alerts to markdown or EDN formats

### Data Flow

```
Feeds (data/feeds.edn)
  ↓
Fetcher → Normalized Items
  ↓
Matcher (with rules.edn) → Alerts
  ↓
Core → Formatted Output / Export
  ↓
Checkpoint Storage (data/checkpoints.edn)
```

### State Management

- **Checkpoints**: Stored in-memory (`checkpoints` atom) and persisted to `data/checkpoints.edn`
  - Tracks last-seen timestamp per feed to avoid reprocessing items
  - Updated after each successful feed processing run

- **Configuration**: Loaded once on namespace initialization
  - `rules` - Alert rules from `data/rules.edn`
  - `feeds` - Feed subscriptions from `data/feeds.edn`

### Data Files

All configuration is stored in `data/` as EDN files:

- **feeds.edn**: Vector of feed maps with `:feed-id` and `:url`
- **rules.edn**: Vector of rule maps with `:id`, `:must`, `:should`, `:must-not`, `:min-should-match`
- **checkpoints.edn**: Map of feed-id to last-seen Date (auto-managed)

**File path constants** (defined in `alert-scout.core`):
- `default-rules-path` - "data/rules.edn"
- `default-feeds-path` - "data/feeds.edn"
- `default-checkpoints-path` - "data/checkpoints.edn"

These private constants centralize path configuration, making it easy to modify paths in one location.

## Important Implementation Details

### Functional Programming Style

This codebase follows functional programming principles:

- **Separation of concerns**: Data processing is separated from major side effects
  - `process-feed!` fetches data and logs items, but returns structured data for aggregation
  - `process-feeds!` performs major side effects (alert emission, checkpointing) after all data is collected
- **Avoid mutation**: Use `map`, `mapcat`, and `reduce` instead of `doseq` with atoms
- **Immutable data structures**: Results are built up using lazy sequences and vector transformations

When adding new features, maintain this separation:
```clojure
;; Good - function with side effects returns data for aggregation
(defn process-feed! [rules feed]
  (println "Processing...")  ;; Side effect ok if needed for logging
  {:alerts [...] :items [...]})

;; Then perform major side effects separately
(doseq [result results]
  (emit-alert result))

;; Bad - mixing mutation and side effects
(doseq [feed feeds]
  (swap! state conj (process feed)))
```

**Domain objects as maps**: Functions should accept domain objects (maps) rather than individual fields:
```clojure
;; Good - takes Feed map
(defn fetch-items [{:keys [feed-id url]}]
  ...)

;; Bad - individual parameters
(defn fetch-items [feed-id url]
  ...)
```

This approach:
- Makes function signatures more flexible (easy to add new fields)
- Enforces schema contracts (Feed maps are validated)
- Improves composability (pass entire object through pipeline)
- Reduces parameter coupling

### Type Hints and Reflection

This codebase uses Java interop extensively for feed parsing and date operations. **Always add proper type hints** to avoid reflection warnings:

```clojure
;; Good - type hints prevent reflection
(.after ^Date timestamp last-seen)
(.getValue ^SyndContent content)

;; Bad - causes reflection warnings
(.after timestamp last-seen)
```

Run `lein check` to verify no reflection warnings are introduced.

### Variable Naming

**Never shadow Clojure core functions** with local variable names. For example, use `last-seen` instead of `last` to avoid shadowing `clojure.core/last`.

### Null Handling

Feeds may return items with `nil` dates or missing fields. Always handle:
- `nil` checkpoint values on first run (`(or (nil? last-seen) ...)`)
- Missing `:published-at` with `when-let` guards
- Ensure Date comparisons have both operands non-nil

### Schema Validation with Malli

This project uses [Malli](https://github.com/metosin/malli) for runtime data validation:

- **Validate at boundaries**: Data is validated when loading from files or external sources
- **Clear error messages**: Invalid data produces human-readable error explanations
- **Fail fast**: Better to fail on startup than corrupt data or crash later
- **Optional validation**: Can be disabled in tight loops for performance

Examples:
```clojure
;; Validate individual values
(schemas/validate schemas/Feed {:feed-id "hn" :url "..."})

;; Get validation errors
(schemas/explain schemas/Rule invalid-rule)
;=> {:user-id ["should be a string"]}

;; Storage layer validates automatically
(storage/add-feed! "data/feeds.edn" "" "")  ;; Throws validation error

;; Generate test data
(require '[malli.generator :as mg])
(mg/generate schemas/Feed)
```

See `doc/malli-examples.md` for comprehensive examples and benefits.

### REPL-Driven Development

This project is designed for REPL-driven development:
- Configuration is loaded at namespace initialization via `def` forms
- To reload configuration changes, use `:reload-all` flag when requiring namespaces
- `process-feeds!` returns structured data `{:alerts [...] :items-processed n}` for inspection

### Bug Fixing Workflow

When fixing bugs, follow a strict Test-Driven Development (TDD) approach:

1. **Reproduce the bug** - Verify it exists and understand the trigger
2. **Write a failing test** - Create a test that captures the bug
3. **Verify the test fails** - Ensure the test actually catches the bug
4. **Fix the bug** - Implement the minimal fix
5. **Verify the test passes** - Ensure the fix works
6. **Run full test suite** - Ensure no regressions
7. **Document the bug** - Add comments explaining the fix

**Critical:** Always verify the test fails before fixing. If the test passes when it should fail, it doesn't actually catch the bug.

See `doc/bug-fixing-workflow.md` for the complete workflow, examples, and best practices.

## Project Structure

```
src/
  alert-scout/          # Main application logic
    core.clj           # Orchestration (process-feeds!, process-feed!)
    excerpts.clj       # Core excerpt extraction logic
    fetcher.clj        # RSS/Atom feed fetching
    formatter.clj      # Output formatting (terminal, markdown, EDN)
    matcher.clj        # Rule matching engine with excerpt generation
    storage.clj        # Data persistence with validation
    schemas.clj        # Malli schemas for domain objects
  my-stuff/            # Legacy/scratch namespace
    core.clj           # Not actively used

data/                  # Configuration and state (EDN files)
  feeds.edn           # Feed subscriptions
  rules.edn           # Alert rules
  checkpoints.edn     # Last-seen timestamps (auto-managed)

doc/                   # Documentation
  malli-examples.md   # Comprehensive Malli usage examples
  bug-fixing-workflow.md  # TDD workflow for fixing bugs
```

### Namespace Organization

**formatter.clj** - All output formatting (terminal, markdown, EDN):
- Terminal: ANSI color codes for matched terms
- Markdown: Bold formatting for exports
- EDN: Structured data serialization
- Pure functions: formatting is data transformation

**storage.clj** - Data persistence layer:
- EDN file I/O for configuration (feeds, rules, users, checkpoints)
- Alert export via `save-alerts!` (uses formatter namespace)
- Malli schema validation at boundaries
- State management for checkpoints (atom + file sync)

**excerpts.clj** - Core excerpt extraction:
- Text position finding with case-insensitive matching
- Word boundary detection for clean truncation
- Excerpt consolidation when matches are close together
- Pure functions following functional purity principle

**Note**: `save-alerts!` lives in storage.clj (not core.clj) because storage owns all file persistence operations. Core.clj focuses on orchestration only.

## Active Technologies
- Clojure 1.11+ (existing project language) (001-content-excerpts)
- N/A (in-memory data transformations, no persistence for excerpts) (001-content-excerpts)

## Recent Changes
- 001-content-excerpts: Added Clojure 1.11+ (existing project language)

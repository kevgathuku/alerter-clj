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

;; Run the feed processor (uses feeds from data/feeds.edn)
(core/run-once)

;; Run with custom feeds
(core/run-once [{:feed-id "test" :url "https://example.com/rss"}])

;; Save alerts to file
(def result (core/run-once))
(core/save-alerts! (:alerts result) "reports/alerts.md" :markdown)

;; Validate data structures (useful during development)
(require '[alert-scout.schemas :as schemas])
(schemas/valid? schemas/Feed {:feed-id "hn" :url "https://news.ycombinator.com/rss"})
(schemas/explain schemas/Feed {:feed-id ""})  ;; See why data is invalid
```

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

## Architecture

### Core Components

The application follows a pipeline architecture with clear separation of concerns:

1. **Fetcher** (`alert-scout.fetcher`) - RSS/Atom feed fetching and parsing
   - Uses Rome Tools library for feed parsing
   - Takes Feed maps (`:feed-id`, `:url`) as input
   - Normalizes feed entries into a common map structure with `:feed-id`, `:item-id`, `:title`, `:link`, `:published-at`, `:content`, `:categories`

2. **Matcher** (`alert-scout.matcher`) - Rule matching engine
   - Implements boolean search logic with `must`, `should`, `must-not`, and `min-should-match` fields
   - Rules are grouped by user-id for multi-user support
   - Matching is case-insensitive and searches both title and content

3. **Storage** (`alert-scout.storage`) - Data persistence layer
   - Handles EDN file I/O for users, rules, feeds, and checkpoints
   - Maintains in-memory checkpoint state using atoms
   - Provides CRUD operations for feed management
   - Validates data against schemas when loading/saving

4. **Schemas** (`alert-scout.schemas`) - Data validation using Malli
   - Defines schemas for all domain objects (Feed, Rule, User, FeedItem, Alert)
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
  - `users` - User definitions from `data/users.edn`
  - `rules` - Alert rules from `data/rules.edn`
  - `feeds` - Feed subscriptions from `data/feeds.edn`
  - `rules-by-user` - Derived grouping of rules by user-id

### Data Files

All configuration is stored in `data/` as EDN files:

- **feeds.edn**: Vector of feed maps with `:feed-id` and `:url`
- **rules.edn**: Vector of rule maps with `:id`, `:user-id`, `:must`, `:should`, `:must-not`, `:min-should-match`
- **users.edn**: Vector of user maps with `:id` and `:email`
- **checkpoints.edn**: Map of feed-id to last-seen Date (auto-managed)

## Important Implementation Details

### Functional Programming Style

This codebase follows functional programming principles:

- **Separation of concerns**: Data processing is separated from side effects
  - `process-feed` is pure (no side effects) - it only transforms data
  - `run-once` performs side effects (printing, checkpointing) after all data is collected
- **Avoid mutation**: Use `map`, `mapcat`, and `reduce` instead of `doseq` with atoms
- **Immutable data structures**: Results are built up using lazy sequences and vector transformations

When adding new features, maintain this separation:
```clojure
;; Good - pure function returns data
(defn process-feed [feed]
  {:alerts [...] :items [...]})

;; Then perform side effects separately
(doseq [result results]
  (println result))

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
- `run-once` returns structured data `{:alerts [...] :items-processed n}` for inspection

## Project Structure

```
src/
  alert-scout/          # Main application logic
    core.clj           # Orchestration, formatting, exports
    fetcher.clj        # RSS/Atom feed fetching
    matcher.clj        # Rule matching engine
    storage.clj        # Data persistence with validation
    schemas.clj        # Malli schemas for domain objects
  my-stuff/            # Legacy/scratch namespace
    core.clj           # Not actively used

data/                  # Configuration and state (EDN files)
  feeds.edn           # Feed subscriptions
  rules.edn           # Alert rules
  users.edn           # User definitions
  checkpoints.edn     # Last-seen timestamps (auto-managed)

doc/                   # Documentation
  malli-examples.md   # Comprehensive Malli usage examples
```

## Active Technologies
- Clojure 1.11+ (existing project language) (001-content-excerpts)
- N/A (in-memory data transformations, no persistence for excerpts) (001-content-excerpts)

## Recent Changes
- 001-content-excerpts: Added Clojure 1.11+ (existing project language)

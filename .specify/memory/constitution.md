# Alert Scout Constitution

<!--
SYNC IMPACT REPORT
==================
Version: 1.0.0 (Initial Constitution)
Ratified: 2025-12-30
Last Amended: 2025-12-30

Constitution Creation:
- Derived from existing CLAUDE.md project standards
- Focus areas: Code quality, testing, UX consistency, performance
- Principles aligned with Clojure functional programming best practices

Principles Added:
1. Functional Purity & Immutability
2. Schema-First Data Validation
3. Type Safety & Reflection-Free Java Interop
4. REPL-Driven Development Workflow
5. User-Facing Data Consistency

Templates Requiring Updates:
✅ plan-template.md - Constitution Check section will reference these principles
✅ spec-template.md - Requirements align with data validation and UX principles
✅ tasks-template.md - Task categorization supports functional and testing principles

Follow-up TODOs:
- None - all placeholders filled
-->

## Core Principles

### I. Functional Purity & Immutability

**Principle**: Business logic MUST be implemented as pure functions that transform immutable data structures. Side effects MUST be isolated to system boundaries.

**Rules**:
- Data processing functions return values; they do NOT perform I/O, mutation, or logging
- Use `map`, `mapcat`, `reduce`, `filter` for transformations - NEVER `doseq` with atoms for data collection
- Functions accept domain objects (maps) rather than individual parameters
- Results are built using lazy sequences and persistent data structures
- Side effects (printing, file I/O, checkpointing) occur ONLY in top-level orchestration functions

**Rationale**: Pure functions are testable, composable, and reasoning-friendly. Separating data transformation from side effects enables REPL-driven development and prevents subtle bugs from shared mutable state.

**Example**:
```clojure
;; GOOD - pure function returns data
(defn process-feed [{:keys [feed-id url]}]
  {:alerts [...] :items [...]})

;; Then perform side effects separately
(doseq [result results]
  (println result))

;; BAD - mixing mutation and side effects
(doseq [feed feeds]
  (swap! state conj (process feed)))
```

### II. Schema-First Data Validation

**Principle**: All domain objects MUST have Malli schemas. Data MUST be validated at system boundaries (file I/O, external APIs, user input). Invalid data MUST fail fast with clear error messages.

**Rules**:
- Every domain entity (Feed, Rule, User, FeedItem, Alert) has a Malli schema in `schemas.clj`
- Storage layer validates all data when loading/saving
- Validation errors produce human-readable explanations using `schemas/explain`
- Schema validation MAY be skipped in tight inner loops for performance, but boundaries are NON-NEGOTIABLE
- Test data generation uses `malli.generator/generate` for property-based testing

**Rationale**: Runtime validation catches configuration errors early, prevents data corruption, and provides clear feedback to users. Schema-driven development documents data contracts and enables automatic test data generation.

**Example**:
```clojure
;; Validate at boundary
(schemas/validate schemas/Feed {:feed-id "hn" :url "..."})

;; Get validation errors
(schemas/explain schemas/Rule invalid-rule)
;=> {:user-id ["should be a string"]}

;; Storage layer validates automatically
(storage/add-feed! "data/feeds.edn" "" "")  ; Throws validation error
```

### III. Type Safety & Reflection-Free Java Interop

**Principle**: All Java interop calls MUST include type hints to eliminate reflection warnings. Variable names MUST NOT shadow Clojure core functions. `lein check` MUST pass with zero reflection warnings before any commit.

**Rules**:
- Type hint all Java method calls: `(.after ^Date timestamp last-seen)`
- Type hint function parameters when they interact with Java: `(defn process [^SyndFeed feed] ...)`
- Never use variable names that shadow `clojure.core` (e.g., use `last-seen` not `last`)
- Run `lein check` before committing - CI WILL reject reflection warnings
- Document Java types in function docstrings when interop is central to the function

**Rationale**: Reflection is slow and causes runtime errors that could be caught at compile time. Type hints improve performance and catch type mismatches early. Shadowing core functions leads to confusing bugs.

**Example**:
```clojure
;; GOOD - type hints prevent reflection
(.after ^Date timestamp last-seen)
(.getValue ^SyndContent content)

;; BAD - causes reflection warnings
(.after timestamp last-seen)
```

### IV. REPL-Driven Development Workflow

**Principle**: The codebase MUST support interactive development via REPL. Configuration MUST be reloadable. Functions MUST return structured data for inspection, not just side effects.

**Rules**:
- Top-level `def` forms load configuration at namespace initialization
- Configuration changes require `:reload-all` flag: `(require '[alert-scout.core :as core] :reload-all)`
- Public functions return data maps (e.g., `{:alerts [...] :items-processed n}`) for REPL inspection
- Side-effect functions (`run-once`, `save-alerts!`) return results AND perform effects
- Test helpers support REPL experimentation (e.g., `(core/run-once custom-feeds)`)

**Rationale**: REPL-driven development enables rapid feedback, interactive debugging, and exploratory programming. Returning structured data allows developers to inspect results, compose operations, and test edge cases interactively.

**Example**:
```clojure
;; Reload after changes
(require '[alert-scout.core :as core] :reload-all)

;; Run and inspect
(def result (core/run-once))
(:items-processed result)  ; Number of items
(count (:alerts result))   ; Alert count

;; Test with custom data
(core/run-once [{:feed-id "test" :url "..."}])
```

### V. User-Facing Data Consistency

**Principle**: All user-facing interfaces (CLI output, exports, error messages) MUST follow consistent formats. Terminal output MUST use ANSI color codes for clarity. Exports MUST support both machine-readable (EDN) and human-readable (Markdown) formats.

**Rules**:
- CLI output uses color codes: alerts in bold, summaries with separators, errors in red
- Export functions support `:markdown` and `:edn` formats via keyword arguments
- Error messages include context (file path, validation errors, feed URL)
- Date/time formatting uses consistent ISO-8601 style
- Data files use EDN format with clear structure (vectors of maps, not nested complexity)

**Rationale**: Consistent interfaces reduce cognitive load and make the tool predictable. Color-coded output improves scannability in terminals. Multiple export formats support both human review and programmatic processing.

**Example**:
```clojure
;; Export to Markdown for human review
(core/save-alerts! alerts "reports/daily.md" :markdown)

;; Export to EDN for processing
(core/save-alerts! alerts "data/alerts.edn" :edn)

;; Consistent error messages
"Failed to load feeds.edn: {:feed-id [\"should have at least 1 characters\"]}"
```

## Testing Standards

**Principle**: Tests MUST cover critical business logic. Schema validation MUST be tested. Java interop edge cases (nil dates, missing fields) MUST be tested.

**Testing Requirements**:
- Unit tests for matcher logic (boolean rules, case-insensitivity)
- Schema validation tests for all domain objects
- Null handling tests (nil checkpoints, missing published dates)
- Integration tests for feed fetching (using test fixtures or mocked data)
- `lein test` MUST pass on JDK 21 and 25 (CI requirement)

**Test Organization**:
- Tests in `test/` directory mirror `src/` structure
- Test namespaces use `-test` suffix (e.g., `alert-scout.matcher-test`)
- Property-based tests use `malli.generator/generate` for test data
- Edge cases explicitly documented in test docstrings

**Current Test Coverage**:
- 22 tests with 79 assertions
- Matcher logic: 100% coverage
- Schema validation: all domain objects
- Null handling: checkpoint edge cases, missing dates

## Performance Standards

**Principle**: Performance-critical paths MUST avoid reflection and unnecessary allocations. Feed processing MUST be efficient enough for 15-minute cron intervals.

**Performance Requirements**:
- Zero reflection warnings (`lein check` passes)
- Type hints on all Java interop in hot paths
- Lazy sequences for large data transformations (avoid realizing entire collections)
- Checkpoint system prevents reprocessing items (last-seen timestamps)
- Memory-efficient: process feeds one at a time, don't load all items into memory

**Benchmarking**:
- Feed processing time acceptable for 15-30 minute cron jobs
- Startup time under 5 seconds (JVM warmup excluded)
- Memory usage proportional to largest single feed, not total feeds

**Optimization Discipline**:
- Profile before optimizing (use VisualVM or YourKit)
- Measure impact with realistic data (100+ item feeds)
- Document performance assumptions in code comments

## Quality Gates

**Pre-Commit Checklist**:
1. `lein test` passes (all tests green)
2. `lein check` passes (zero reflection warnings)
3. No shadowed core functions in new code
4. Schema validation added for new domain objects
5. Pure functions for business logic, side effects isolated
6. REPL-testable (functions return data, accept domain maps)

**CI/CD Requirements**:
- GitHub Actions runs on all pushes (Quick Check workflow)
- Pull requests run full CI (multiple JDK versions, artifact builds)
- Both workflows cache dependencies (`~/.m2`, `~/.lein`, `target/`)
- Reflection warnings fail the build
- Uberjar builds and uploads artifacts (7-day retention)

**Code Review Focus**:
- Functional purity: data transformations separate from side effects
- Type safety: Java interop has type hints
- Schema validation: boundaries validated, errors clear
- REPL-friendliness: can test interactively
- Consistent UX: CLI output, exports, error messages follow standards

## Documentation Standards

**Required Documentation**:
- `CLAUDE.md`: Architecture, commands, design principles (MUST stay in sync with constitution)
- `README.md`: User-facing quick start, features, configuration
- `doc/malli-examples.md`: Schema validation examples
- Docstrings for public functions (especially non-obvious logic)

**Documentation Updates**:
- Architecture changes MUST update CLAUDE.md
- New features MUST update README.md
- Schema changes MUST update malli-examples.md
- Breaking changes MUST document migration path

## Governance

**Amendment Process**:
1. Propose change via pull request to `.specify/memory/constitution.md`
2. Update dependent templates (plan, spec, tasks) in same PR
3. Update CLAUDE.md if principles conflict or extend guidance
4. Increment version semantically:
   - MAJOR: Remove/redefine principle (backward incompatible)
   - MINOR: Add new principle or materially expand section
   - PATCH: Clarify wording, fix typos, non-semantic refinements
5. Include Sync Impact Report at top of constitution as HTML comment
6. Require approval before merging

**Compliance Review**:
- All PRs MUST verify constitution compliance
- CI enforces reflection warnings, test passage
- Code reviews verify functional purity, schema validation, type safety
- Complexity that violates principles MUST be justified (document in plan.md Complexity Tracking)

**Principle Precedence**:
- Constitution overrides all other guidance
- CLAUDE.md provides implementation details for constitution principles
- When constitution and CLAUDE.md conflict, constitution wins (update CLAUDE.md)

**Version**: 1.0.0 | **Ratified**: 2025-12-30 | **Last Amended**: 2025-12-30

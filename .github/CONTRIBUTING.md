# Contributing to Alert Scout

Thank you for your interest in contributing! This document provides guidelines for contributing to the project.

## Development Setup

1. **Prerequisites:**
   - JDK 11, 17, or 21
   - [Leiningen](https://leiningen.org/) installed

2. **Clone and setup:**
   ```bash
   git clone https://github.com/YOUR_USERNAME/my-stuff.git
   cd my-stuff
   lein deps
   ```

3. **Run tests:**
   ```bash
   lein test
   ```

## Before Submitting a Pull Request

Ensure all of these pass locally:

```bash
# 1. Tests pass
lein test

# 2. No reflection warnings
lein check

# 3. Code compiles
lein compile

# 4. Data schemas are valid
# (automatically validated during tests)
```

## Code Standards

### Functional Programming Style

- Separate pure functions from side effects
- Avoid mutation (no `atom`, `ref`, `swap!` in business logic)
- Use `map`, `filter`, `reduce` instead of imperative loops

**Example:**
```clojure
;; Good - pure function
(defn process-items [items]
  (map transform-item items))

;; Bad - mutation
(defn process-items [items]
  (let [result (atom [])]
    (doseq [item items]
      (swap! result conj (transform-item item)))
    @result))
```

### Type Hints Required

Always add type hints for Java interop to avoid reflection:

```clojure
;; Good
(.after ^Date timestamp last-seen)

;; Bad - causes reflection warning
(.after timestamp last-seen)
```

Run `lein check` to verify no reflection warnings.

### Schema Validation

Add Malli schemas for new domain objects:

```clojure
;; In alert-scout.schemas
(def NewDomainObject
  [:map
   [:id [:string {:min 1}]]
   [:required-field :string]])
```

Validate at system boundaries (file I/O, external APIs).

### Testing

Write minimal tests focused on business logic:

1. **What to test:**
   - Rule matching logic
   - Data transformations
   - Edge cases (nil values, empty collections)
   - Integration points

2. **What NOT to test:**
   - Schema validation (Malli handles this)
   - Simple getters/setters
   - Third-party library behavior

3. **Test structure:**
   ```clojure
   (deftest test-feature-name
     (testing "Descriptive test case"
       (is (= expected (my-function input)))))
   ```

## Pull Request Process

1. **Create a feature branch:**
   ```bash
   git checkout -b feature/your-feature-name
   ```

2. **Make your changes and commit:**
   ```bash
   git add .
   git commit -m "Add feature: brief description"
   ```

3. **Push and create PR:**
   ```bash
   git push origin feature/your-feature-name
   ```

4. **Wait for CI checks:**
   - All tests must pass
   - No reflection warnings
   - Builds on JDK 11, 17, and 21

5. **Address review feedback**

## Common Tasks

### Adding a new feed

```clojure
(require '[alert-scout.storage :as storage])
(storage/add-feed! "data/feeds.edn" "feed-id" "https://example.com/rss")
```

### Adding a new rule

Edit `data/rules.edn`:
```clojure
{:id "new-rule"
 :user-id "your-user"
 :must ["required-term"]
 :should ["optional1" "optional2"]
 :must-not ["excluded"]
 :min-should-match 1}
```

### Running locally

```bash
lein repl
(require '[alert-scout.core :as core])
(core/run-once)
```

## Architecture Guidelines

See [CLAUDE.md](../CLAUDE.md) for detailed architecture documentation.

**Key principles:**
- Validate data at boundaries
- Keep business logic pure
- Use schemas for documentation and validation
- Maintain separation of concerns

## Questions?

- Check [CLAUDE.md](../CLAUDE.md) for architecture details
- Review [doc/malli-examples.md](../doc/malli-examples.md) for schema usage
- Look at existing tests for examples

## License

By contributing, you agree that your contributions will be licensed under the same license as the project (EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0).

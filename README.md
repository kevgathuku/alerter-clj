# Alert Scout

![CI](https://github.com/kevgathuku/alerter-clj/workflows/CI/badge.svg)
![Quick Check](https://github.com/kevgathuku/alerter-clj/workflows/Quick%20Check/badge.svg)

**Alert Scout** is an intelligent RSS/Atom feed monitoring system that watches your favorite feeds and sends you personalized alerts based on powerful rule-matching logic.

Never miss important content again - let Alert Scout filter the noise and surface what matters to you.

## Features

- 🎯 **Smart Rule Matching** - Define complex boolean rules with `must`, `should`, `must-not`, and `min-should-match` logic
- 📡 **Multi-Feed Support** - Monitor unlimited RSS/Atom feeds simultaneously
- 👥 **Multi-User** - Different alert rules for different users
- ✅ **Schema Validation** - Runtime data validation with [Malli](https://github.com/metosin/malli) ensures data integrity
- 🎨 **Beautiful Output** - Color-coded terminal alerts with formatted summaries
- 📤 **Export Options** - Save alerts as Markdown or EDN for further processing
- 🔄 **Checkpoint System** - Avoid reprocessing items with automatic last-seen tracking
- ⚡ **Functional Design** - Pure functions, immutable data, no mutation in business logic
- 🧪 **Well Tested** - 22 tests with 79 assertions covering critical logic

## Quick Start

```bash
# Clone and setup
git clone https://github.com/kevgathuku/alerter-clj.git
cd alerter-clj
lein deps

# Run tests to verify setup
lein test

# Start REPL and run
lein repl
```

```clojure
;; In the REPL
(require '[alert-scout.core :as core])

;; Process all configured feeds
(core/run-once)

;; Save alerts to file
(def result (core/run-once))
(core/save-alerts! (:alerts result) "my-alerts.md" :markdown)
```

## Installation

### Prerequisites

- **JDK 21 or 25** - [Download here](https://adoptium.net/)
- **Leiningen** - [Install guide](https://leiningen.org/)

### Build from Source

```bash
# Clone repository
git clone https://github.com/kevgathuku/alerter-clj.git
cd alerter-clj

# Install dependencies
lein deps

# Build standalone JAR
lein uberjar

# Run the JAR
java -jar target/uberjar/my-stuff-0.1.0-SNAPSHOT-standalone.jar
```

## Configuration

Alert Scout uses EDN files in the `data/` directory for configuration.

### Configure Feeds

**`data/feeds.edn`**

```clojure
[{:feed-id "hn" :url "https://news.ycombinator.com/rss"}
 {:feed-id "blog" :url "https://kevgathuku.dev/rss.xml"}]
```

### Configure Rules

**`data/rules.edn`**

```clojure
[{:id "rails-deployment"
  :user-id "alice"
  :must ["rails"]                          ;; Must contain "rails"
  :should ["docker" "kamal" "deploy"]      ;; Should have at least 1 of these
  :must-not ["test"]                       ;; Must NOT contain "test"
  :min-should-match 1}

 {:id "ai-news"
  :user-id "bob"
  :must ["ai"]
  :should ["llm" "gpt" "claude"]
  :min-should-match 1}]
```

### Configure Users

**`data/users.edn`**

```clojure
[{:id "alice" :email "alice@example.com"}
 {:id "bob" :email "bob@example.com"}]
```

## Usage

### Basic Usage

```clojure
(require '[alert-scout.core :as core])

;; Run once (uses feeds from data/feeds.edn)
(core/run-once)
```

**Example Output:**

```
→ Checking feed: hn (https://news.ycombinator.com/rss)

■ MATCH [hn] Rule: rails-deployment User: alice
  Deploying Rails with Docker and Kamal
  https://news.ycombinator.com/item?id=123456
  Published: 2025-12-29 15:30

═══ SUMMARY ═══
Total alerts: 3
  alice: 2 alerts
    - hn: 2
  bob: 1 alerts
    - blog: 1

Processed 15 new items across 2 feeds
```

### Advanced Usage

#### Custom Feeds

```clojure
;; Run with custom feeds
(core/run-once [{:feed-id "custom" :url "https://example.com/rss"}])
```

#### Export Alerts

```clojure
;; Get results
(def result (core/run-once))

;; Export to Markdown
(core/save-alerts! (:alerts result) "reports/daily-alerts.md" :markdown)

;; Export to EDN for processing
(core/save-alerts! (:alerts result) "data/alerts.edn" :edn)
```

#### Manage Feeds Programmatically

```clojure
(require '[alert-scout.storage :as storage])

;; Add a feed
(storage/add-feed! "data/feeds.edn" "lobsters" "https://lobste.rs/rss")

;; Remove a feed
(storage/remove-feed! "data/feeds.edn" "lobsters")

;; Get a specific feed
(storage/get-feed "data/feeds.edn" "hn")
```

#### Schema Validation

```clojure
(require '[alert-scout.schemas :as schemas])

;; Validate data
(schemas/valid? schemas/Feed {:feed-id "test" :url "https://example.com/rss"})
;; => true

;; See validation errors
(schemas/explain schemas/Feed {:feed-id "" :url ""})
;; => {:feed-id ["should have at least 1 characters"]
;;     :url ["should have at least 1 characters"]}

;; Generate test data
(require '[malli.generator :as mg])
(mg/generate schemas/Feed)
;; => {:feed-id "abc", :url "https://..."}
```

## Rule Matching Logic

Rules support powerful boolean logic:

- **`must`** - All terms must be present (AND logic)
- **`should`** - At least `min-should-match` terms should be present (OR logic)
- **`must-not`** - None of these terms can be present (NOT logic)
- **`min-should-match`** - Minimum number of `should` terms required

**Example:**

```clojure
{:must ["rails"]                    ;; Must contain "rails"
 :should ["docker" "kubernetes"]    ;; AND (docker OR kubernetes)
 :must-not ["test"]                 ;; AND NOT "test"
 :min-should-match 1}               ;; At least 1 should term required
```

All matching is **case-insensitive** and searches both title and content.

## Development

### Running Tests

```bash
# All tests
lein test

# Specific namespace
lein test alert-scout.matcher-test

# Specific test
lein test :only alert-scout.matcher-test/test-match-rule-combined
```

### Checking for Issues

```bash
# Check for reflection warnings
lein check

# Compile
lein compile
```

### REPL Development

```clojure
;; Reload after changes
(require '[alert-scout.core :as core] :reload-all)

;; Run with custom configuration
(def my-feeds [{:feed-id "test" :url "https://example.com/rss"}])
(core/run-once my-feeds)

;; Inspect results
(def result (core/run-once))
(:items-processed result)  ;; Number of items processed
(count (:alerts result))   ;; Number of alerts generated
```

## Architecture

Alert Scout follows a functional pipeline architecture:

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

**Key Principles:**

- Pure functions for business logic
- Side effects isolated to boundaries
- Schema validation at system boundaries
- Immutable data structures throughout

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## Documentation

- **[CLAUDE.md](CLAUDE.md)** - Complete architecture and development guide
- **[doc/malli-examples.md](doc/malli-examples.md)** - Schema validation examples
- **[doc/startup-validation.md](doc/startup-validation.md)** - Startup validation guide
- **[doc/email-notifications-design.md](doc/email-notifications-design.md)** - Email notification system design
- **[.github/CONTRIBUTING.md](.github/CONTRIBUTING.md)** - Contribution guidelines
- **[.github/workflows/README.md](.github/workflows/README.md)** - CI/CD documentation

## Continuous Integration

The project uses GitHub Actions for CI/CD:

- ✅ Tests on JDK 21 and 25
- ✅ Reflection warning checks
- ✅ Schema validation
- ✅ Automatic uberjar builds
- ✅ Artifact retention for 7 days

See the [CI documentation](.github/workflows/README.md) for details.

## Contributing

Contributions are welcome! Please see [CONTRIBUTING.md](.github/CONTRIBUTING.md) for guidelines.

**Before submitting a PR:**

1. Run `lein test` - all tests must pass
2. Run `lein check` - no reflection warnings
3. Follow functional programming style (see CLAUDE.md)
4. Add tests for new features

## Roadmap

- [ ] **Email notifications** - Daily/weekly digest emails ([Design Doc](doc/email-notifications-design.md))
  - SQLite queue for alert tracking
  - User preference management
  - HTML email templates
  - SMTP delivery with retry logic
- [ ] Web UI for rule management
- [ ] Webhook support
- [ ] RSS feed aggregator UI
- [ ] Machine learning for rule suggestions
- [ ] Mobile app

## FAQ

**Q: How often should I run Alert Scout?**
A: Use a cron job or systemd timer. We recommend every 15-30 minutes for active feeds.

**Q: Can I use this for monitoring non-RSS sources?**
A: Currently RSS/Atom only. Support for other sources could be added via the fetcher layer.

**Q: How do I reset checkpoints?**
A: Delete or edit `data/checkpoints.edn`. This will reprocess all items on next run.

**Q: Are email alerts supported?**
A: Not yet, but planned! Email notifications will use daily/weekly digests to prevent spam. See the [design document](doc/email-notifications-design.md) for details.

## License

Copyright © 2025

This program and the accompanying materials are made available under the
terms of the Eclipse Public License 2.0 which is available at
<https://www.eclipse.org/legal/epl-2.0>.

This Source Code may also be made available under the following Secondary
Licenses when the conditions for such availability set forth in the Eclipse
Public License, v. 2.0 are satisfied: GNU General Public License as published by
the Free Software Foundation, either version 2 of the License, or (at your
option) any later version, with the GNU Classpath Exception which is available
at <https://www.gnu.org/software/classpath/license.html>.

## Acknowledgments

Built with:

- [Clojure](https://clojure.org/) - Functional programming language
- [Rome Tools](https://rometools.github.io/rome/) - RSS/Atom feed parsing
- [Malli](https://github.com/metosin/malli) - Data validation
- [Leiningen](https://leiningen.org/) - Build automation

---

**Made with ❤️ and Clojure**

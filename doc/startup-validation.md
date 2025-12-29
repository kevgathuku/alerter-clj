# Startup Validation Demo

This document demonstrates how startup validation protects the application from invalid configuration.

## How It Works

When the `alert-scout.core` namespace is loaded, all configuration is validated:

```clojure
;; In alert-scout.core
(def users (storage/load-users "data/users.edn"))  ;; Validates users
(def rules (storage/load-rules "data/rules.edn"))  ;; Validates rules
(def feeds (storage/load-feeds "data/feeds.edn"))  ;; Validates feeds
```

If ANY configuration is invalid, the namespace **fails to load** with clear error messages.

## Benefits

✅ **Fail fast** - Catch config errors immediately, not during runtime
✅ **Clear errors** - Know exactly what's wrong and where
✅ **Prevent corruption** - Invalid config never enters the system
✅ **Safe deployment** - App won't start with bad config

## Examples

### Example 1: Invalid User Email

**Bad config in `data/users.edn`:**

```clojure
[{:id "kevin" :email "not-an-email"}  ;; Invalid email format!
 {:id "admin" :email "admin@example.com"}]
```

**What happens:**

```clojure
(require '[alert-scout.core :as core])
;=> Execution error (ExceptionInfo): Invalid users in data/users.edn
;   {:path "data/users.edn"
;    :errors {0 {:email ["should match regex"]}}}
```

The namespace **won't load** until you fix the email.

### Example 2: Missing Required Fields

**Bad config in `data/feeds.edn`:**

```clojure
[{:feed-id "hn"}  ;; Missing :url!
 {:feed-id "blog" :url "https://kevgathuku.dev/rss.xml"}]
```

**What happens:**

```clojure
(require '[alert-scout.core :as core])
;=> Execution error (ExceptionInfo): Invalid feeds in data/feeds.edn
;   {:path "data/feeds.edn"
;    :errors {0 {:url ["missing required key"]}}}
```

### Example 3: Wrong Data Type

**Bad config in `data/rules.edn`:**

```clojure
[{:id "rails"
  :user-id 123  ;; Should be string, not number!
  :must ["rails"]}]
```

**What happens:**

```clojure
(require '[alert-scout.core :as core])
;=> Execution error (ExceptionInfo): Invalid rules in data/rules.edn
;   {:path "data/rules.edn"
;    :errors {0 {:user-id ["should be a string"]}}}
```

### Example 4: Empty Values

**Bad config in `data/feeds.edn`:**

```clojure
[{:feed-id "" :url ""}  ;; Empty strings not allowed!
 {:feed-id "hn" :url "https://news.ycombinator.com/rss"}]
```

**What happens:**

```clojure
(require '[alert-scout.core :as core])
;=> Execution error (ExceptionInfo): Invalid feeds in data/feeds.edn
;   {:path "data/feeds.edn"
;    :errors {0 {:feed-id ["should have at least 1 characters"]
;                :url ["should have at least 1 characters"]}}}
```

## Testing Startup Validation in REPL

You can test this yourself:

```clojure
;; 1. Backup your good config
$ cp data/feeds.edn data/feeds.edn.backup

;; 2. Create invalid config
$ echo '[{:feed-id "" :url ""}]' > data/feeds.edn

;; 3. Try to load the namespace
lein repl
(require '[alert-scout.core :as core] :reload-all)
;=> Execution error (ExceptionInfo): Invalid feeds in data/feeds.edn

;; 4. Restore good config
$ mv data/feeds.edn.backup data/feeds.edn

;; 5. Now it works
(require '[alert-scout.core :as core] :reload-all)
;=> nil (success!)
```

## Comparison: Before vs After

### Before Startup Validation

```clojure
;; Config loads silently with typos
(def users (load-edn "data/users.edn"))
;=> [{:id "kevin" :emial "kevin@example.com"}]  ;; Typo: "emial"

;; Later in the code...
(:email (first users))
;=> nil  ;; Silent failure! Where's the bug?
```

### After Startup Validation

```clojure
;; Typo caught immediately
(def users (storage/load-users "data/users.edn"))
;=> Execution error (ExceptionInfo): Invalid users in data/users.edn
;   {:errors {0 {:email ["missing required key"]}}}

;; Fix the typo, namespace loads successfully
(def users (storage/load-users "data/users.edn"))
;=> [{:id "kevin" :email "kevin@example.com"}]  ;; ✓ Valid!
```

## Production Deployment Safety

In production, this prevents:

1. **Deploying with broken config** - App won't start
2. **Runtime surprises** - Config errors surface immediately
3. **Data corruption** - Invalid data never enters the system
4. **Silent failures** - Errors are loud and clear

## Performance Impact

Validation happens **once at startup**, not on every request:

- ✅ **Startup**: ~few milliseconds (one-time cost)
- ✅ **Runtime**: Zero overhead (config already validated)
- ✅ **Hot reload**: Re-validates when you reload namespace in REPL

## Best Practices

1. **Always validate at boundaries** (file I/O, external APIs)
2. **Validate once, use many times** (don't re-validate in loops)
3. **Provide clear error messages** (done automatically by Malli)
4. **Test with invalid config** (ensure validation catches issues)

## Summary

Startup validation ensures:

- 🛡️ Config is valid before app starts
- 📝 Clear error messages when something's wrong
- 🚫 Invalid data can't enter the system
- 🔄 Safe hot-reloading in development
- 🚀 Confident deployments to production

The small startup cost is worth the safety and reliability!

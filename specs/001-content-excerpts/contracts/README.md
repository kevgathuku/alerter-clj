# Contracts

This feature does not have external API contracts as it is an internal library feature for a CLI application.

## Internal Contracts (Function Signatures)

The excerpt functionality exposes the following public functions:

### alert-scout.excerpts namespace

```clojure
(defn generate-excerpts
  "Generate excerpts from text showing matched terms with context.
  
  Args:
    text - String to extract excerpts from
    matched-terms - Vector of terms to find and highlight
    opts - Optional map with :context-chars and :max-excerpts
  
  Returns:
    Vector of excerpt maps with :text, :matched-terms, :source"
  [text matched-terms opts])

(defn generate-excerpts-for-item
  "Generate excerpts from both title and content of a feed item.
  
  Args:
    item - FeedItem map with :title and :content
    matched-terms - Vector of terms to find
  
  Returns:
    Vector of Excerpt maps"
  [item matched-terms])
```

### Enhanced alert-scout.matcher namespace

```clojure
(defn match-item
  "Return alerts with excerpts for items matching rules.
  
  Args:
    rules-by-user - Map of user-id to vector of rules
    item - FeedItem to match against
  
  Returns:
    Vector of Alert maps (now includes :excerpts field)"
  [rules-by-user item])
```

## Schema Contracts

See `data-model.md` for Malli schemas that serve as contracts:
- `Excerpt` schema
- Enhanced `Alert` schema

These schemas are enforced at runtime and provide validation contracts.


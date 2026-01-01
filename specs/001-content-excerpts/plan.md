# Implementation Plan: Content Excerpts in Alerts

**Branch**: `001-content-excerpts` | **Date**: 2025-12-30 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/001-content-excerpts/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Add excerpt generation to alerts showing matched content with context. When an alert is generated, extract snippets (max 3 per alert) showing where terms matched with 50 characters of surrounding context. Display excerpts with highlighted matched terms in terminal (ANSI colors) and markdown (bold formatting). Support EDN export with structured excerpt data.

## Technical Context

**Language/Version**: Clojure 1.11+ (existing project language)
**Primary Dependencies**:
- clojure.string (built-in, for text processing)
- Malli (existing, for schema validation)
- Existing ANSI color functions in alert-scout.core

**Storage**: N/A (in-memory data transformations, no persistence for excerpts)
**Testing**: `lein test` with clojure.test framework (existing)
**Target Platform**: JVM (JDK 21 or 25, per existing CI requirements)
**Project Type**: Single project (command-line tool)
**Performance Goals**: <5ms excerpt generation per alert (spec requirement SC-002)
**Constraints**:
- Zero reflection warnings (`lein check` must pass)
- Memory-efficient processing (don't load entire feed content into memory at once)
- Fixed excerpt settings (50 chars context, 3 max excerpts)

**Scale/Scope**:
- Process 10-100 feed items per run
- Content size: 100-10,000 characters per item
- Up to 3 excerpts per alert

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

### I. Functional Purity & Immutability
- ✅ **PASS**: Excerpt extraction will be pure functions
  - `find-term-positions`, `extract-excerpt`, `generate-excerpts` return data
  - No mutation, side effects isolated to display/export layers
  - Accepts domain objects (Alert, FeedItem maps)

### II. Schema-First Data Validation
- ✅ **PASS**: Will add Malli schemas for excerpts
  - New schema: `Excerpt` with `:text`, `:matched-terms`, `:source` fields
  - Enhanced schema: `Alert` includes `:excerpts` field
  - Validation at matcher boundary when creating alerts

### III. Type Safety & Reflection-Free Java Interop
- ✅ **PASS**: Minimal Java interop (only string operations)
  - String methods will be type-hinted where needed
  - No new Java dependencies
  - Existing reflection-free patterns followed

### IV. REPL-Driven Development Workflow
- ✅ **PASS**: Functions return structured data for inspection
  - `generate-excerpts` returns vector of excerpt maps
  - Compatible with existing `run-once` REPL workflow
  - Can test excerpt generation in isolation

### V. User-Facing Data Consistency
- ✅ **PASS**: Consistent with existing output formats
  - Terminal: ANSI color highlighting (existing pattern)
  - Markdown: Bold formatting (existing pattern)
  - EDN: Structured data export (existing pattern)

### Testing Standards
- ✅ **PASS**: Will add comprehensive tests
  - Unit tests for term position finding
  - Unit tests for excerpt extraction and consolidation
  - Integration tests with matcher
  - Edge cases: nil content, short content, many matches

### Performance Standards
- ✅ **PASS**: Meets <5ms per alert requirement
  - String operations are efficient in JVM
  - Lazy sequences for processing
  - No reflection (pure Clojure string functions)

**Initial Status**: ✅ ALL GATES PASSED - Ready for Phase 0 research

**Post-Phase 1 Re-check**: ✅ ALL GATES STILL PASS
- Research confirmed pure functional approach (Phase 0)
- Data model follows schema-first principle (Phase 1)
- No new Java dependencies or reflection risks
- REPL workflow preserved (quickstart.md examples)
- Consistent with existing Alert Scout patterns

## Project Structure

### Documentation (this feature)

```text
specs/001-content-excerpts/
├── spec.md              # Feature specification (completed)
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (text processing patterns)
├── data-model.md        # Phase 1 output (Excerpt, enhanced Alert schemas)
├── quickstart.md        # Phase 1 output (REPL usage examples)
├── checklists/
│   └── requirements.md  # Specification quality checklist (completed)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)

```text
src/alert_scout/
├── core.clj            # [MODIFY] Orchestration only, use formatter namespace
├── formatter.clj       # [NEW] Output formatting (terminal, markdown, EDN)
├── matcher.clj         # [MODIFY] Add excerpt generation to match-item
├── schemas.clj         # [MODIFY] Add Excerpt schema, update Alert schema
├── excerpts.clj        # [NEW] Core excerpt extraction logic
├── fetcher.clj         # [NO CHANGE] Feed fetching unchanged
└── storage.clj         # [NO CHANGE] Storage layer unchanged

test/alert_scout/
├── core_test.clj       # [MODIFY] Update tests for orchestration
├── formatter_test.clj  # [NEW] Tests for formatting functions
├── matcher_test.clj    # [MODIFY] Add tests for excerpt integration
├── schemas_test.clj    # [MODIFY] Add tests for new Excerpt schema
└── excerpts_test.clj   # [NEW] Unit tests for excerpt extraction

data/
├── feeds.edn           # [NO CHANGE] Existing feed configuration
├── rules.edn           # [NO CHANGE] Existing rule configuration
└── users.edn           # [NO CHANGE] Existing user configuration
```

**Structure Decision**: Single project structure. This is a pure Clojure CLI application following the existing alert-scout namespace organization. New excerpt functionality will live in a dedicated `excerpts.clj` namespace with integration points in `matcher.clj` (excerpt generation) and `formatter.clj` (display/export). A new `formatter.clj` namespace separates presentation concerns from orchestration in `core.clj`.

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

No violations - all constitution principles satisfied.

## Architectural Decisions

### Formatter Namespace Separation

**Decision**: Create dedicated `alert-scout.formatter` namespace for all output formatting logic

**Rationale**:
- **Separation of concerns**: Data transformation (excerpts) separated from presentation (formatting)
- **Single responsibility**: Core.clj becomes orchestration only, formatter.clj handles all output
- **Reusability**: Formatting functions can be reused across different output contexts
- **Testability**: Easier to test formatting logic in isolation
- **Constitution alignment**: Follows functional purity principle (formatting as pure transformation)

**Scope of formatter namespace**:
- Terminal formatting: `format-alert` with ANSI colors
- Markdown export: `alerts->markdown` with bold highlighting
- EDN export: `alerts->edn` with structured data
- Highlight functions: `highlight-terms-terminal`, `highlight-terms-markdown`
- Shared formatting utilities

**Migration from core.clj**:
- Move `format-alert` → `formatter/format-alert`
- Move `alerts->markdown` → `formatter/alerts->markdown`
- Move `alerts->edn` → `formatter/alerts->edn`
- Move color/highlighting utilities → formatter namespace
- Move `save-alerts!` → `storage/save-alerts!` (storage owns persistence)
- Core.clj retains: orchestration, REPL interface, `run-once`

**Note**: During implementation, `save-alerts!` was moved to `storage.clj` as storage is responsible for all file persistence operations. This provides better separation of concerns than keeping it in core.clj.

**Benefits**:
1. Core.clj becomes simpler (orchestration only)
2. Formatter logic independently testable
3. Clear boundary between data and presentation
4. Easier to add new export formats (JSON, HTML, etc.)
5. Follows existing pattern (matcher, fetcher, storage are separate namespaces)

**Implementation impact**:
- New file: `src/alert_scout/formatter.clj`
- New test file: `test/alert_scout/formatter_test.clj`
- Modified: `src/alert_scout/core.clj` (require formatter, use formatter functions)
- Additional tasks: ~3-4 tasks for namespace creation and migration

## Phase Summary

### Phase 0: Research (Completed)

**Output**: `research.md`

**Key Decisions**:
1. Use built-in `clojure.string` for text processing (no new dependencies)
2. Word boundary detection via nearest-space search
3. Sort + reduce algorithm for excerpt consolidation
4. Format-specific highlighting (ANSI for terminal, bold for markdown)
5. Malli schemas for Excerpt and enhanced Alert
6. <5ms performance target via content length limits
7. Integration at matcher's `match-item` function

### Phase 1: Design & Contracts (Completed)

**Outputs**:
- `data-model.md` - Detailed schemas for Excerpt and enhanced Alert
- `quickstart.md` - REPL usage examples and testing guide
- `contracts/README.md` - Function signatures (no external APIs)

**Key Artifacts**:
1. **Excerpt Schema**: `:text`, `:matched-terms`, `:source` with validation
2. **Enhanced Alert Schema**: Added optional `:excerpts` field (max 3)
3. **REPL Examples**: 16 practical examples for testing and debugging
4. **Validation Points**: At `generate-excerpts` return and `match-item` return

### Phase 2: Task Generation (Next)

**Command**: `/speckit.tasks`

**Expected Outputs**:
- `tasks.md` - Detailed task breakdown organized by user story
- Tasks for core excerpt logic (`excerpts.clj`)
- Tasks for matcher integration (`matcher.clj`)
- Tasks for display updates (`core.clj`)
- Tasks for schema updates (`schemas.clj`)
- Tasks for comprehensive testing

## Implementation Readiness

✅ **Technical Context**: Fully defined (Clojure, existing dependencies)
✅ **Constitution Check**: All gates passed (pre and post Phase 1)
✅ **Research**: Complete with all patterns decided
✅ **Data Model**: Schemas defined with validation strategy
✅ **Quickstart Guide**: REPL examples ready for development
✅ **Agent Context**: Updated with feature information

**Status**: Ready for `/speckit.tasks` command to generate implementation tasks

## Notes for Implementation

### Critical Files to Modify

1. **src/alert_scout/excerpts.clj** (NEW)
   - Core excerpt extraction logic
   - Pure functions following constitution principle I
   - Type hints for string operations (principle III)

2. **src/alert_scout/formatter.clj** (NEW)
   - All output formatting functions (terminal, markdown, EDN)
   - `format-alert` with ANSI colors
   - `alerts->markdown`, `alerts->edn`
   - `highlight-terms-terminal`, `highlight-terms-markdown`

3. **src/alert_scout/matcher.clj** (MODIFY)
   - Add `get-matched-terms` function
   - Enhance `match-item` to generate excerpts
   - Integrate with excerpts namespace

4. **src/alert_scout/core.clj** (MODIFY)
   - Orchestration only (run-once, save-alerts!)
   - Require and use formatter namespace
   - Remove formatting functions (moved to formatter.clj)

5. **src/alert_scout/schemas.clj** (MODIFY)
   - Add `Excerpt` schema
   - Enhance `Alert` schema with `:excerpts` field
   - Ensure backwards compatibility

6. **test/** (NEW + MODIFY)
   - New: `test/alert_scout/excerpts_test.clj`
   - New: `test/alert_scout/formatter_test.clj`
   - Modify existing test files for integration

### Testing Strategy

**Unit Tests** (excerpts_test.clj):
- `find-term-positions` with various inputs
- `extract-excerpt` with word boundaries
- `consolidate-excerpts` with overlaps
- Schema validation (valid/invalid cases)

**Unit Tests** (formatter_test.clj):
- `format-alert` with excerpts and ANSI colors
- `alerts->markdown` with bold highlighting
- `alerts->edn` with structured data
- `highlight-terms-terminal` and `highlight-terms-markdown`

**Integration Tests** (matcher_test.clj):
- `match-item` returns alerts with excerpts
- Excerpts generated for matching items
- Edge cases: nil content, short content, many matches

**Integration Tests** (core_test.clj):
- `run-once` uses formatter namespace correctly
- `save-alerts!` works with new formatter functions

**Performance Tests**:
- Verify <5ms per alert requirement
- Test with 500-5000 character content
- Test with 1-10 matching terms

### Development Workflow

1. Start with `excerpts.clj` core functions (TDD)
2. Add schemas to `schemas.clj`
3. Create `formatter.clj` with formatting functions
4. Integrate excerpts into `matcher.clj`
5. Update `core.clj` to use formatter namespace
6. Add comprehensive tests (excerpts, formatter, integration)
7. Run `lein check` (zero reflection warnings)
8. Run `lein test` (all tests pass)
9. Manual REPL testing per quickstart.md

**REPL-First Development**:
- Test each function interactively as you write it
- Use `(require '... :reload-all)` after changes
- Validate with sample data before integration

---

**End of Plan** - Next step: `/speckit.tasks`

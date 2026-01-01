# Feature Specification: Content Excerpts in Alerts

**Feature Branch**: `001-content-excerpts`
**Created**: 2025-12-30
**Status**: Draft
**Input**: User description: "Generate a feature spec based on @doc/excerpt-generation-design.md"

## Clarifications

### Session 2025-12-30

- Q: Should users be able to customize excerpt display settings (context length, max excerpts)? → A: No. Fixed settings: max 3 excerpts, 50 characters context, non-configurable

## User Scenarios & Testing

### User Story 1 - View Alert Context (Priority: P1)

As an Alert Scout user, when I receive an alert, I want to see a preview of the matched content showing exactly where and why the match occurred, so I can quickly assess relevance without clicking through to the full article.

**Why this priority**: This is the core value proposition of the feature. Without context, users must visit every link to determine relevance, reducing the efficiency of the alert system.

**Independent Test**: Generate an alert for a feed item with multiple matching terms. Verify that excerpts appear showing the matched terms highlighted with surrounding context.

**Acceptance Scenarios**:

1. **Given** a feed item matches a rule with terms "rails" and "api", **When** the alert is displayed in the terminal, **Then** I see excerpt snippets showing where "rails" and "api" appear in the content with surrounding text
2. **Given** a feed item has matches in both title and content, **When** the alert is displayed, **Then** I see excerpts labeled separately as "[Title]" and "[Content]"
3. **Given** a feed item matches multiple terms in different locations, **When** the alert is generated, **Then** I see up to 3 excerpts showing the most relevant matches
4. **Given** an alert with excerpts, **When** I export to markdown format, **Then** the excerpts are included with matched terms in bold formatting

---

### User Story 2 - Export Alerts with Context (Priority: P2)

As an Alert Scout user, when I export alerts to markdown or EDN format, I want excerpts included in the output so I can review context offline or share alerts with colleagues.

**Why this priority**: Extends the excerpt feature to all output formats, but terminal display is the primary use case.

**Independent Test**: Generate alerts with excerpts and export to both markdown and EDN. Verify excerpts are included with appropriate formatting.

**Acceptance Scenarios**:

1. **Given** alerts with excerpts, **When** I export to markdown, **Then** excerpts appear with matched terms in **bold** format
2. **Given** alerts with excerpts, **When** I export to EDN, **Then** excerpts are included as structured data with `:text`, `:matched-terms`, and `:source` fields
3. **Given** an exported markdown file, **When** I open it in a markdown viewer, **Then** the excerpts are readable and properly formatted

---

### Edge Cases

- What happens when a matched term appears 50+ times in content?
  - System generates up to 3 excerpts by default, consolidating nearby matches
- What happens when matched terms are very close together (within 20 characters)?
  - Excerpts are merged into a single excerpt to avoid redundancy
- What happens when content is very short (< 100 characters)?
  - Excerpt shows the entire content without ellipsis
- What happens when content has HTML tags?
  - Current scope: process as-is (HTML stripping is future enhancement)
- What happens when a term matches across a word boundary (e.g., "rail" matching "derail")?
  - System performs whole-word matching to avoid false positives
- What happens if content is missing or nil?
  - System only extracts excerpts from available fields (title or content)

## Requirements

### Functional Requirements

- **FR-001**: System MUST extract excerpts from both title and content fields of feed items when they contain matching terms
- **FR-002**: System MUST show exactly 50 characters of surrounding context (before and after) for each matched term
- **FR-003**: System MUST visually distinguish matched terms in excerpts using highlighting (ANSI colors for terminal, bold for markdown)
- **FR-004**: System MUST consolidate overlapping excerpts when multiple terms match within close proximity (default: 20 characters)
- **FR-005**: System MUST limit the number of excerpts per alert to exactly 3 maximum
- **FR-006**: System MUST use ellipsis (...) to indicate truncated content at excerpt boundaries
- **FR-007**: System MUST preserve word boundaries when extracting excerpts (don't break words mid-character)
- **FR-008**: System MUST label excerpts by source ("[Title]" or "[Content]") to indicate where the match occurred
- **FR-009**: System MUST include excerpts in all output formats (terminal display, markdown export, EDN export)
- **FR-010**: System MUST perform case-insensitive matching when finding term positions
- **FR-011**: System MUST handle nil/missing content gracefully without errors
- **FR-012**: System MUST identify which specific terms from the rule matched (combination of must/should terms)

### Key Entities

- **Excerpt**: A snippet of text showing matched content with context
  - Attributes: text (with context), matched terms, source (title or content), original position
  - Relationships: belongs to an Alert, references multiple matched terms

- **Alert** (enhanced): Existing entity now includes excerpts
  - Attributes: rule-id, item (feed item), excerpts (new)
  - Relationships: contains multiple Excerpts
  - Note: user-id field removed during implementation (rule-based matching only)

## Success Criteria

### Measurable Outcomes

- **SC-001**: Users can determine alert relevance without clicking links in 80% of cases (measured by reduced click-through rate to feed items)
- **SC-002**: Excerpt generation adds less than 5ms processing time per alert (measured with realistic feed content of 500-5000 characters)
- **SC-003**: Users see excerpts with matched terms highlighted with exactly 50 characters of context before and after each match
- **SC-004**: System successfully processes alerts for content ranging from 100 to 10,000+ characters without errors
- **SC-005**: 90% of excerpts show complete sentences or meaningful phrases (not broken mid-word)
- **SC-006**: Exported markdown files with excerpts are readable and properly formatted when opened in standard markdown viewers

## Assumptions

- Feed content is primarily plain text or simple HTML (complex HTML stripping is future enhancement)
- Most relevant matches occur in the first 5000 characters of content
- Users primarily review alerts in terminal output (exports are secondary use case)
- Performance target of <5ms per excerpt assumes standard laptop hardware
- Context of 50 characters (before and after) around a match provides sufficient understanding
- Exactly 3 excerpts per alert provide sufficient context without overwhelming output
- Word boundary detection works for English text (multi-language support is future enhancement)
- Matched terms are typically 3-20 characters in length
- Fixed excerpt settings (50 chars context, 3 max excerpts) are suitable for all users without requiring customization

## Dependencies

- Existing matcher logic that identifies which rules matched which items
- Existing ANSI color formatting functions in core.clj
- Existing export functions (markdown, EDN) in core.clj
- Case-insensitive text matching already implemented in matcher

## Scope

### In Scope

- Extract excerpts from title and content fields
- Highlight matched terms in terminal and markdown output
- Fixed context length (50 characters) and max excerpt count (3)
- Consolidate overlapping excerpts
- Smart truncation at word boundaries
- Label excerpts by source (title/content)
- Include excerpts in all export formats
- Handle nil/missing content gracefully

### Out of Scope

- HTML tag stripping (process content as-is in this release)
- Ranking or sorting excerpts by relevance
- Sentence boundary detection for smarter truncation
- Multi-language word boundary detection
- Caching of excerpts for repeated items
- User-configurable excerpt settings (fixed values only)
- Excerpt generation for historical alerts (applies to new alerts only)

## Open Questions

None - all critical decisions have been resolved based on the design document.

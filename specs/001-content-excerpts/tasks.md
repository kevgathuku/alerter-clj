# Tasks: Content Excerpts in Alerts

**Input**: Design documents from `/specs/001-content-excerpts/`
**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests are included based on existing Alert Scout testing standards (constitution requirement)

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Include exact file paths in descriptions

## Path Conventions

- **Single project**: `src/`, `test/` at repository root
- Paths use alert-scout namespace structure

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure

- [X] T001 Verify Clojure project structure follows src/alert_scout/ pattern
- [X] T002 Confirm Malli dependency available in project.clj
- [X] T003 [P] Review existing ANSI color functions in src/alert_scout/core.clj for reuse

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

- [X] T004 Add Excerpt schema to src/alert_scout/schemas.clj
- [X] T005 Enhance Alert schema with optional :excerpts field in src/alert_scout/schemas.clj
- [X] T006 Add schema validation tests for Excerpt in test/alert_scout/schemas_test.clj
- [X] T007 Add schema validation tests for enhanced Alert in test/alert_scout/schemas_test.clj

**Checkpoint**: Foundation ready - user story implementation can now begin in parallel

---

## Phase 3: User Story 1 - View Alert Context (Priority: P1) 🎯 MVP

**Goal**: Users see previews of matched content with highlighted terms in terminal display

**Independent Test**: Generate alert for feed item with multiple matching terms. Verify excerpts appear with matched terms highlighted and surrounding context.

### Core Excerpt Logic (US1)

- [X] T008 [P] [US1] Create alert-scout.excerpts namespace in src/alert_scout/excerpts.clj
- [X] T009 [P] [US1] Implement find-term-positions function in src/alert_scout/excerpts.clj
- [X] T010 [P] [US1] Implement extract-excerpt with word boundary detection in src/alert_scout/excerpts.clj
- [X] T011 [US1] Implement consolidate-excerpts with overlap detection in src/alert_scout/excerpts.clj (depends on T010)
- [X] T012 [US1] Implement generate-excerpts main function in src/alert_scout/excerpts.clj (depends on T009, T010, T011)
- [X] T013 [US1] Implement generate-excerpts-for-item for title and content in src/alert_scout/excerpts.clj (depends on T012)

### Matcher Integration (US1)

- [X] T014 [US1] Add get-matched-terms function to src/alert_scout/matcher.clj (depends on T013)
- [X] T015 [US1] Enhance match-item to generate excerpts in src/alert_scout/matcher.clj (depends on T014)

### Formatter Namespace (US1)

- [X] T016 [P] [US1] Create alert-scout.formatter namespace in src/alert_scout/formatter.clj
- [X] T017 [P] [US1] Implement highlight-terms-terminal for ANSI colors in src/alert_scout/formatter.clj
- [X] T018 [US1] Implement format-alert with excerpt display in src/alert_scout/formatter.clj (depends on T017)
- [X] T019 [US1] Update core.clj to require and use formatter namespace (depends on T018)

### Unit Tests - Excerpts (US1)

- [ ] T020 [P] [US1] Create test namespace in test/alert_scout/excerpts_test.clj
- [ ] T021 [P] [US1] Test find-term-positions with case-insensitive matching in test/alert_scout/excerpts_test.clj
- [ ] T022 [P] [US1] Test find-term-positions with multiple occurrences in test/alert_scout/excerpts_test.clj
- [ ] T023 [P] [US1] Test extract-excerpt with word boundaries in test/alert_scout/excerpts_test.clj
- [ ] T024 [P] [US1] Test extract-excerpt with short content in test/alert_scout/excerpts_test.clj
- [ ] T025 [P] [US1] Test consolidate-excerpts with overlapping excerpts in test/alert_scout/excerpts_test.clj
- [ ] T026 [P] [US1] Test consolidate-excerpts with distant excerpts in test/alert_scout/excerpts_test.clj
- [ ] T027 [P] [US1] Test generate-excerpts with max 3 limit in test/alert_scout/excerpts_test.clj
- [ ] T028 [P] [US1] Test generate-excerpts-for-item with title and content in test/alert_scout/excerpts_test.clj
- [ ] T029 [P] [US1] Test generate-excerpts-for-item with nil content in test/alert_scout/excerpts_test.clj

### Unit Tests - Formatter (US1)

- [X] T030 [P] [US1] Create test namespace in test/alert_scout/formatter_test.clj
- [X] T031 [P] [US1] Test highlight-terms-terminal with ANSI colors in test/alert_scout/formatter_test.clj
- [X] T032 [P] [US1] Test format-alert with excerpts in test/alert_scout/formatter_test.clj

### Integration Tests (US1)

- [ ] T033 [US1] Test match-item returns alerts with excerpts in test/alert_scout/matcher_test.clj (depends on T015)
- [ ] T034 [US1] Test match-item handles nil content gracefully in test/alert_scout/matcher_test.clj (depends on T015)
- [ ] T035 [US1] Test core.clj uses formatter namespace correctly in test/alert_scout/core_test.clj (depends on T019)

**Checkpoint**: At this point, User Story 1 should be fully functional and testable independently

---

## Phase 4: User Story 2 - Export Alerts with Context (Priority: P2)

**Goal**: Excerpts included in markdown and EDN exports

**Independent Test**: Generate alerts with excerpts and export to both markdown and EDN. Verify excerpts are included with appropriate formatting.

### Export Functions (US2)

- [ ] T036 [P] [US2] Implement highlight-terms-markdown for bold formatting in src/alert_scout/formatter.clj
- [ ] T037 [P] [US2] Implement alerts->markdown with excerpt support in src/alert_scout/formatter.clj
- [ ] T038 [P] [US2] Implement alerts->edn with excerpt support in src/alert_scout/formatter.clj
- [ ] T039 [US2] Update save-alerts! in core.clj to use formatter functions (depends on T037, T038)

### Export Tests (US2)

- [ ] T040 [P] [US2] Test highlight-terms-markdown preserves case in test/alert_scout/formatter_test.clj
- [ ] T041 [P] [US2] Test alerts->markdown includes excerpts with bold formatting in test/alert_scout/formatter_test.clj
- [ ] T042 [P] [US2] Test alerts->edn includes excerpts as structured data in test/alert_scout/formatter_test.clj
- [ ] T043 [US2] Test markdown export renders correctly (manual verification via quickstart.md example) (depends on T039)

**Checkpoint**: At this point, User Stories 1 AND 2 should both work independently

---

## Phase 5: Polish & Cross-Cutting Concerns

**Purpose**: Improvements that affect multiple user stories

- [ ] T044 [P] Run lein check to verify zero reflection warnings
- [ ] T045 [P] Run lein test to verify all tests pass
- [ ] T046 Verify performance <5ms per alert with realistic data (500-5000 char content)
- [ ] T047 [P] Test edge cases from spec.md (50+ matches, close matches, short content)
- [ ] T048 [P] Manual REPL testing per quickstart.md examples
- [ ] T049 Update CLAUDE.md to document formatter namespace pattern
- [ ] T050 Verify backwards compatibility (alerts without excerpts still work)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies - can start immediately
- **Foundational (Phase 2)**: Depends on Setup completion - BLOCKS all user stories
- **User Stories (Phase 3+)**: All depend on Foundational phase completion
  - User stories can then proceed in parallel (if staffed)
  - Or sequentially in priority order (P1 → P2)
- **Polish (Final Phase)**: Depends on all desired user stories being complete

### User Story Dependencies

- **User Story 1 (P1)**: Can start after Foundational (Phase 2) - No dependencies on other stories
- **User Story 2 (P2)**: Can start after Foundational (Phase 2) - No dependency on US1 (but US1 provides foundation)

### Within Each User Story

**User Story 1 Dependencies**:

- T008-T010 can run in parallel (different functions in excerpts.clj)
- T011 depends on T010 (uses extract-excerpt)
- T012 depends on T009, T010, T011 (uses all previous functions)
- T013 depends on T012 (uses generate-excerpts)
- T014 depends on T013 (uses generate-excerpts-for-item)
- T015 depends on T014 (uses get-matched-terms)
- T016, T017 can run in parallel (formatter namespace creation and functions)
- T018 depends on T017 (formatter functions for format-alert)
- T019 depends on T018 (core.clj uses formatter)
- T020-T029 can run in parallel (excerpts unit tests)
- T030-T032 can run in parallel (formatter unit tests)
- T033-T035 depend on T015, T019 (integration tests need implementation)

**User Story 2 Dependencies**:

- T036-T038 can run in parallel (different export functions in formatter.clj)
- T039 depends on T037, T038 (core.clj uses formatter export functions)
- T040-T042 can run in parallel (formatter export tests)
- T043 depends on T039 (manual verification)

### Parallel Opportunities

- All Setup tasks (T001-T003) can run in parallel
- All Foundational tests (T006-T007) can run in parallel
- User Story 1: Core logic (T008-T010), Formatter setup (T016-T017), Unit tests (T020-T032)
- User Story 2: Export functions (T036-T038), Export tests (T040-T042)
- Polish tasks (T044-T048) can mostly run in parallel

---

## Parallel Example: User Story 1

```bash
# Launch core excerpt functions in parallel:
Task: "Implement find-term-positions function in src/alert_scout/excerpts.clj"
Task: "Implement extract-excerpt with word boundary detection in src/alert_scout/excerpts.clj"

# After consolidation is ready, launch tests in parallel:
Task: "Test find-term-positions with case-insensitive matching in test/alert_scout/excerpts_test.clj"
Task: "Test find-term-positions with multiple occurrences in test/alert_scout/excerpts_test.clj"
Task: "Test extract-excerpt with word boundaries in test/alert_scout/excerpts_test.clj"
Task: "Test extract-excerpt with short content in test/alert_scout/excerpts_test.clj"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (T001-T003)
2. Complete Phase 2: Foundational (T004-T007) - CRITICAL - blocks all stories
3. Complete Phase 3: User Story 1 (T008-T030)
4. **STOP and VALIDATE**: Test User Story 1 independently using REPL
5. Run lein check and lein test
6. Demo terminal display with excerpts

### Incremental Delivery

1. Complete Setup + Foundational → Foundation ready
2. Add User Story 1 → Test independently → Terminal display works (MVP!)
3. Add User Story 2 → Test independently → Export formats work
4. Run Polish phase → All quality gates pass
5. Each story adds value without breaking previous stories

### Parallel Team Strategy

With multiple developers:

1. Team completes Setup + Foundational together
2. Once Foundational is done:
   - Developer A: User Story 1 core logic (T008-T013)
   - Developer B: User Story 1 tests (T018-T027)
3. After core logic complete:
   - Developer A: User Story 1 integration (T014-T017)
   - Developer B: User Story 2 exports (T031-T037)
4. Stories complete and integrate independently

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- Each user story should be independently completable and testable
- Verify lein check passes (zero reflection warnings) before committing
- Commit after each logical group of tasks
- Stop at any checkpoint to validate story independently
- Avoid: vague tasks, same file conflicts, cross-story dependencies that break independence

---

## Task Summary

**Total Tasks**: 50

**By Phase**:

- Setup: 3 tasks
- Foundational: 4 tasks (BLOCKS all user stories)
- User Story 1 (P1): 28 tasks (core logic + formatter + tests)
- User Story 2 (P2): 8 tasks (exports + tests)
- Polish: 7 tasks

**By Type**:

- Implementation: 29 tasks (excerpts + formatter + integration)
- Tests: 21 tasks (42% test coverage)
- Setup/Verification: 7 tasks

**Parallel Opportunities**: 28 tasks can run in parallel (56%)

**MVP Scope**: Phase 1 + Phase 2 + Phase 3 (User Story 1) = 35 tasks

**Critical Path**: Setup → Foundational → US1 Core → US1 Formatter → US1 Integration (11 sequential tasks)

**Estimated Effort** (assuming constitution-compliant REPL-driven development):

- Setup + Foundational: 2 hours
- User Story 1: 8-10 hours (core logic + formatter + tests + integration)
- User Story 2: 2-3 hours (exports + tests)
- Polish: 1-2 hours
- **Total**: ~14-17 hours for complete feature

**Architectural Impact**:

- New namespace: `alert-scout.formatter` separates presentation from orchestration
- Cleaner separation of concerns aligned with constitution principles
- Additional ~2 hours for formatter namespace but improved maintainability

**Constitution Compliance Built-in**:

- ✅ Pure functions (excerpts.clj returns data)
- ✅ Schema validation (Excerpt and Alert schemas)
- ✅ Zero reflection (all string operations type-hinted as needed)
- ✅ REPL testable (all functions return data)
- ✅ Comprehensive tests (17 test tasks)

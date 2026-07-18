# Incomplete Comparison Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Prevent incomplete Bean property analysis from being reported as `SAFE`.

**Architecture:** Replace the resolver's property-only return value with a result carrying both properties and completeness. The analyzer keeps known property evidence, preserves confirmed `RISK`, and promotes otherwise incomplete comparisons to `REVIEW`.

**Tech Stack:** Java 17, JavaParser Symbol Solver, JUnit 5, Maven.

---

### Task 1: Reproduce the false SAFE result

**Files:**
- Create: `src/test/java/com/example/beanutils/scanner/analysis/IncompletePropertyResolutionTest.java`

- [ ] Create a temporary Maven source project whose Source and Target extend an unavailable parent type.
- [ ] Scan the project and assert the call is `REVIEW`, with a `property-model-incomplete` trace.
- [ ] Run `./mvnw -Dtest=IncompletePropertyResolutionTest test` and verify it fails because the current result is `SAFE`.

### Task 2: Carry property-model completeness

**Files:**
- Create: `src/main/java/com/example/beanutils/scanner/analysis/BeanPropertyResolution.java`
- Modify: `src/main/java/com/example/beanutils/scanner/analysis/BeanPropertyResolver.java`
- Modify: `src/main/java/com/example/beanutils/scanner/analysis/DirectCopyAnalyzer.java`

- [ ] Return discovered properties together with a completeness flag.
- [ ] Mark hierarchy, declaration, method-signature, specialization and accessor failures incomplete.
- [ ] Preserve known `RISK`; otherwise promote an incomplete comparison to `REVIEW`.
- [ ] Keep already discovered property rows in the report.
- [ ] Run the targeted test and verify it passes.

### Task 3: Verify and publish

**Files:**
- Modify: `README.md`

- [ ] Document that unresolved property models never produce `SAFE`.
- [ ] Run `./mvnw clean verify` in both repositories.
- [ ] Scan the real multi-module Demo and verify the report is generated.
- [ ] Commit and push the standalone repository, then commit the mirrored workspace changes without touching user reference files.

# Multipage HTML Report Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Generate an offline report index whose rows open standalone finding pages that list every Source and Target Bean property separately.

**Architecture:** Keep scan analysis and JSON output unchanged. Refactor `HtmlReportWriter` into an index writer plus deterministic detail-page generation beside the requested output file; derive Source and Target lists from the complete `PropertyFinding` union using `PropertyMapping` presence semantics.

**Tech Stack:** Java 17, JUnit 5, Maven, self-contained HTML/CSS/JavaScript.

---

### Task 1: Lock the output contract with tests

**Files:**
- Modify: `src/test/java/com/example/beanutils/scanner/report/ReportWriterTest.java`

- [ ] Add a report fixture containing mapped, Source-only, Target-only, same-name-not-copyable, and inherited properties.
- [ ] Assert the index contains links to `report-details/finding-0001.html` and no detail side panel.
- [ ] Assert exactly one detail file is generated and it contains separate Source and Target full-property tables.
- [ ] Assert Source-only is absent from the Target table and Target-only is absent from the Source table while both remain in the mapping table.
- [ ] Run `./mvnw -Dtest=ReportWriterTest test` and confirm failure because no detail page is generated.

### Task 2: Generate deterministic detail pages

**Files:**
- Modify: `src/main/java/com/example/beanutils/scanner/report/HtmlReportWriter.java`

- [ ] Derive `<report-base>-details` from the output filename and create it beside the index.
- [ ] Remove stale `finding-*.html` files before writing the current details.
- [ ] Render index rows as accessible anchors with stable `finding-%04d.html` targets.
- [ ] Render each detail page with navigation, call metadata, Bean origins, two side-specific complete property tables, mapping union, and call chain.
- [ ] Keep all content offline and escape dynamic HTML values.
- [ ] Run `./mvnw -Dtest=ReportWriterTest test` and confirm the test passes.

### Task 3: Improve report readability

**Files:**
- Modify: `src/main/java/com/example/beanutils/scanner/report/HtmlReportWriter.java`
- Modify: `README.md`

- [ ] Replace the split-pane CSS with a full-width index optimized for scanning.
- [ ] Give detail pages a readable maximum width, sticky navigation, clear Source/Target visual separation, compact type text, and responsive tables.
- [ ] Document the multi-page output directory and property-list semantics in the README.
- [ ] Run `./mvnw test` and confirm all unit and Demo acceptance tests pass.

### Task 4: Verify the real Demo and publish the standalone repository

**Files:**
- Generated: `target/demo-cross-module-report.html`
- Generated: `target/demo-cross-module-report-details/finding-*.html`
- Generated: `target/demo-cross-module-report.json`

- [ ] Run `./mvnw clean verify` in the monorepo CLI.
- [ ] Scan `beanutils-demo-5.0.7` and verify detail file count equals finding count.
- [ ] Verify every non-REVIEW finding with resolved properties has at least one Source or Target property row.
- [ ] Mirror the scoped implementation into the standalone repository without touching unrelated monorepo reference files.
- [ ] Run `./mvnw clean verify` and the real Demo scan in the standalone repository.
- [ ] Commit and push only the standalone CLI changes, then commit the mirrored monorepo CLI changes with exact paths.

# Bean Property Trace Logging Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Compile the target Maven reactor, then print every resolved Source/Target Bean property model during CLI scanning, once per concrete Bean type.

**Architecture:** Add an injectable, scan-scoped trace logger shared by direct and wrapper analysis. The CLI compiles first, adds reactor class output ahead of source solvers and marks each Bean's evidence source. Preserve the existing quiet library API while the CLI passes `System.out::println`; compile failures explicitly fall back to source.

**Tech Stack:** Java 17, JavaParser resolved types, JUnit 5, Maven, Picocli.

---

### Task 1: Specify trace output and deduplication

**Files:**
- Modify: `src/test/java/com/example/beanutils/scanner/analysis/DirectCopyAnalyzerTest.java`

- [ ] Capture log lines while analyzing the same fixture call twice.
- [ ] Assert Source and Target `BEAN` lines appear once each.
- [ ] Assert mapped, Source-only and Target-only attributes expose read/write type and declaring owner evidence.
- [ ] Assert a missing setter is printed as `writeType=- setterOwner=-`.
- [ ] Run `./mvnw -Dtest=DirectCopyAnalyzerTest test` and confirm compilation fails because the tracing constructor does not exist.

### Task 2: Implement scan-scoped property tracing

**Files:**
- Create: `src/main/java/com/example/beanutils/scanner/analysis/BeanPropertyTraceLogger.java`
- Modify: `src/main/java/com/example/beanutils/scanner/analysis/BeanPropertyResolver.java`
- Modify: `src/main/java/com/example/beanutils/scanner/analysis/DirectCopyAnalyzer.java`
- Modify: `src/main/java/com/example/beanutils/scanner/callgraph/WrapperCallAnalyzer.java`

- [ ] Add a no-op logger and a `Consumer<String>` backed logger with a set keyed by concrete type description.
- [ ] Log hierarchy, sorted business properties, read/write types, getter/setter owners and zero-property results.
- [ ] Log missing or unsupported type declarations as `BEAN-ERROR` once.
- [ ] Inject one logger through direct and wrapper analysis.
- [ ] Run `./mvnw -Dtest=DirectCopyAnalyzerTest test` and confirm all trace assertions pass.

### Task 3: Enable tracing in the CLI

**Files:**
- Modify: `src/main/java/com/example/beanutils/scanner/BeanUtilsRiskScanner.java`
- Modify: `src/main/java/com/example/beanutils/scanner/cli/ScanCommand.java`
- Modify: `src/test/java/com/example/beanutils/scanner/cli/ScannerApplicationTest.java`
- Modify: `README.md`

- [ ] Preserve `scan(ScanRequest)` as the quiet API and add `scan(ScanRequest, Consumer<String>)`.
- [ ] Make the CLI pass `System.out::println` and document the four trace record types.
- [ ] Capture CLI standard output and assert trace lines precede the completion summary.
- [ ] Run `./mvnw clean verify`.

### Task 4: Add compiled-class/Lombok-aware resolution

**Files:**
- Create: `src/main/java/com/example/beanutils/scanner/project/MavenProjectCompiler.java`
- Create: `src/main/java/com/example/beanutils/scanner/source/CompiledProjectClassLoader.java`
- Modify: `src/main/java/com/example/beanutils/scanner/source/TypeSolverFactory.java`
- Modify: `src/main/java/com/example/beanutils/scanner/source/SourceIndexer.java`
- Modify: `src/main/java/com/example/beanutils/scanner/ScanRequest.java`

- [x] Run Maven `compile` / `test-compile` before CLI scanning and stream its output.
- [x] Prefer every reactor module's compiled output over source declarations.
- [x] Disable compiled evidence after a failed build and continue with source fallback.
- [x] Mark every resolved Bean as `compiled-class` or `source-fallback`.
- [x] Test source-only fields against class files containing generated accessors.

### Task 5: Verify and publish

**Files:**
- Generated: `target/demo-scan.log`
- Generated: `target/demo-cross-module-report.html`
- Generated: `target/demo-cross-module-report-details/finding-*.html`

- [ ] Scan the real Demo and capture standard output.
- [ ] Verify each `BEAN` has a matching `BEAN-END` or is represented by `BEAN-ERROR`.
- [ ] Verify inherited `GenericParent` owners and specialized `List<OrderDO>` / `List<OrderDTO>` types appear.
- [ ] Mirror scoped changes to the standalone repository, run all tests, commit and push.

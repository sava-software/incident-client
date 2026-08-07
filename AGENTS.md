# AGENTS.md

Guidance for AI coding agents (and humans) working in this repository.

Machine-specific information (local clone paths of reference repositories) lives in
`AGENTS.local.md`, which is git-ignored. Check it first; if it is missing, create it from
the template at the bottom of this file.

## What this repository is

Java clients for service-incident / alerting APIs, with no dependencies beyond the JDK,
[json-iterator](https://github.com/sava-software/json-iterator), and sava-rpc's
`JsonHttpClient` base. Modules are JPMS modules built with the shared
[sava-build](https://github.com/sava-software/sava-build) convention plugins.

### Module layout

- `incident-core/` — shared scaffolding: `HttpApiClient` builder base, JSON/Properties
  config parsing (`HttpApiClientConfig`), `JsonUtil` (JSON string escaping), `Rfc3339`
  (timestamp formatting), and the provider-neutral `api` package (below).
- `incident-io/` — [incident.io](https://incident.io) API client: create-incident request
  serialization, response parsing, typed error-envelope exceptions.
- `incident-pagerduty/` — PagerDuty **Events API v2** client (`/v2/enqueue`,
  `/v2/change/enqueue`): trigger/acknowledge/resolve alert events and change events.
- `incident-webhook/` — generic webhook POSTer: one shared transport/config/exception
  stack; per-product variance is isolated in `WebhookFormat` (built-ins:
  `GENERIC_JSON` under provider id `webhook`, `SLACK_TEXT` under `slack`, and the
  stateful `TelegramTextFormat` under `telegram` — chat id from config, Bot API
  `sendMessage` endpoint). Notification only — no incident lifecycle,
  `supportsResolve()` is false.
- `incident-examples/` — runnable usage examples; not published.

### Provider-neutral API (`software.sava.incident.core.api`)

Service-level code should depend on this package, not on a provider client, so the
provider can be swapped via configuration:

- `IncidentClient` — `reportIncident(IncidentAlert)` / `resolveIncident(key)`;
  `supportsResolve()` gates providers without a programmatic resolve.
- `IncidentAlert` — key (dedup/idempotency), summary, details, `IncidentSeverity`,
  source, timestamp, custom details. Provider-specific features (PagerDuty links/images,
  incident.io custom fields/roles) stay on the provider clients.
- `IncidentClientException` — implemented by every provider exception; carries
  `canBeRetried()` so retry logic is provider-agnostic.
- `IncidentService` — retrying wrapper over any `IncidentClient` (retry-delay function,
  max-retries / give-up-after variants).

Adapters: `PagerDutyIncidentClient` (dedup-key based, `details` carried in
`custom_details`), `IncidentIoIncidentClient` (workspace-specific severity/type/status
ids are supplied at build time; resolve unsupported), and `WebhookIncidentClient`
(renders the alert with a `WebhookFormat`; resolve unsupported). When extending the
common model, update every adapter (and the webhook formats) and their mapping tests.

## Canonical API references

The JSON shapes in this repo are hand-written; the upstream API schemas are the ground
truth. Verify against them, not against this repo's existing output:

- **PagerDuty** — `https://github.com/PagerDuty/api-schema`;
  `reference/events-v2/openapiv3.json` is the contract for `incident-pagerduty`
  (payload fields, severity enum, required fields, response shapes).
- **incident.io** — the Go SDK (`https://github.com/incident-io/incident-io-go`)
  bundles the full current contract as `openapi3.json`; `IncidentsCreatePayloadV2` /
  `IncidentV2` are the schemas for `incident-io` request/response fields.
  `https://api-docs.incident.io` (Create Incident V2) is the prose companion.

Clone reference repos outside this repository (or in a git-ignored location), record the
paths in `AGENTS.local.md`, and `git pull` an existing clone before comparing.

## JSON serialization conventions

Serialization is hand-rolled (no reflection, no databind). The rules that keep it correct:

- **Every caller-supplied string must be escaped** on its way into a JSON document —
  values, and map keys too. Use `JsonUtil.escapeJson` (full RFC 8259) in `incident-io`,
  and `JsonUtil.escapeJsonRemoveNewLines` in `incident-pagerduty` (PagerDuty string
  fields are single-line by contract: LF/CR are removed rather than escaped).
- **Timestamps** serialize through `OffsetDateTime` (RFC 3339). Never format a
  `ZonedDateTime` directly — region zones render an invalid `[Area/City]` suffix.
- PagerDuty field limits enforced client-side: `summary` truncates at 1024 chars;
  `dedup_key` over 255 chars is rejected.
- Optional fields are omitted when null/blank — never serialized as `"null"` or `""`.
- Response parsing contract: unknown fields are skipped; malformed input fails with a
  `RuntimeException`, never anything else (the fuzz targets enforce this).

## Build, test, and hardening

```
./gradlew check                                  # build + unit tests (all modules)
./gradlew :<module>:pitest<Suite>                # PIT mutation testing:
                                                 #   :incident-core:pitestJson|pitestConfig|pitestApi|pitestClient
                                                 #   :incident-io:pitestRequest|pitestResponse|pitestConfig|pitestAdapter
                                                 #   :incident-pagerduty:pitestPayload|pitestResponse|pitestConfig|pitestAdapter
                                                 #   :incident-webhook:pitestFormat|pitestConfig|pitestAdapter
./gradlew :<module>:fuzz<Target>                 # Jazzer fuzzing:
                                                 #   :incident-io:fuzzRequest|fuzzResponse
                                                 #   :incident-pagerduty:fuzzPayload
                                                 #   :incident-webhook:fuzzFormat
./gradlew :<module>:hardeningHelp                # installed task/property inventory
```

Those fifteen suites and four fuzz targets are this repo's inventory, declared in each
module's `build.gradle.kts` under the `hardening` extension (from sava-build's
`software.sava.build.feature.hardening` plugin). Everything else about the tooling — what
each writer task does, which properties exist, which have been refused — comes from
`hardeningHelp` and `hardeningAgentTemplate`, the installed-version authorities; the
process doctrine behind them lives in sava-build's `HARDENING.md` (local clone path in
`AGENTS.local.md`).

Local hardening records, per module under `config/pitest/`: accepted baselines
`<suite>-accepted.csv`, their acceptance arguments in `README.md`, and their bound
toolchain provenance `<suite>-pitest-version` plus `<suite>-pitest-toolchain.tsv`. A suite
with nothing unkilled has no accepted file at all. No suite times out today, so no
`<suite>-timeouts.csv` exists yet — the timeout audit is armed, not active.

**Ownership:** CI runs `check` only. `hardeningCertify` plus an explicit local
`fuzzAll -PmaxFuzzTime=<seconds> -PmaxParallelFuzzTargets=<count>` campaign are
release-checklist items, run locally with both budgets recorded. Run
`mutationOwnershipAudit` before handoff whenever the production-class inventory changes or
a suite's target/exclusion rules change.

Conventions to preserve:

- Every mutation suite here that targets a package glob sets
  `excludedClasses = listOf("*Test*")`; keep that exclusion when adding one.
- Fuzz targets live in test sources, are deliberately free of Jazzer imports (a public
  static `fuzzerTestOneInput(byte[])`), and are **round-trip oracles**: build with
  arbitrary strings, serialize, assert no raw control characters survive, re-parse with
  `JsonIterator`, and compare against the documented transform. When you add or change a
  serialized field, extend the fuzz oracle *and* add an exact-string unit test — the
  mutation suites only measure what the unit tests pin down.
- Parser fuzz targets follow the "garbage in → RuntimeException out" contract, and their
  seed corpora live under `src/test/resources/fuzz/<target>/`.
- Every fuzz target here declares a `seedCorpus`. A corpus does two independent jobs:
  *bootstrap* (coverage a mutator would take too long to reach — the `response` corpus)
  and *regression* (a committed landing place for findings — the `request`, `payload` and
  `format` corpora; measured to change no mutation score). "The mutator reaches this
  format from scratch" answers only the bootstrap question. Where genuinely neither
  applies, record `declineSeedCorpus`/`declineMutator` with a measured reason. Seed
  provenance goes in a README next to — never inside — the corpus dir.
- Our copy-on-write builder acceptances (`# copy-on-write` / `# defensive-copy` in
  `api-accepted.csv` and `payload-accepted.csv`) argue only the content-equal sibling of
  each `size() > 1 ? copy : as-is` ternary; the mutable-escape direction is killed by the
  record-collection immutability tests (`assertThrows(UnsupportedOperationException, ...)`)
  — keep those assertions when touching the builders.

### Hardening rules (synced from sava-build's HARDENING.md)

The block below is generated — print the installed version with
`./gradlew hardeningAgentTemplate`; `agentsTemplateInSync` (wired into `check`) checks the
marker against it. After a sava-build upgrade, re-diff this block, **act on** each changed
bullet, then update the marker. The block and its marker land with — never ahead of — the
release pin in `settings.gradle.kts` that ships the digest they acknowledge.

<!-- hardening-template sha256:46f7174e51fb -->

> - **Scale verification to the change.** Iterate with the module's `test`
>   task; before handing off, run only the `pitest<Suite>`(s) whose mutated
>   code the change can reach — including suites in dependent modules that
>   call a changed API, and the owning suite for test-only edits (a weakened
>   test is exactly what the ratchet catches). When the production-class inventory
>   changes (add/remove/rename/move), or mutation target/exclusion rules change,
>   also run the cheap whole-population
>   `mutationOwnershipAudit` before handoff. The full `hardeningCertify` — every
>   suite freshly observed, serialized, provenance-bound, diffed against
>   `config/pitest/`, with strict timeout and ownership audits — is the pre-release
>   check, owned by CI or by the release checklist (this repo records which); it is
>   not the inner loop.
> - A new unkilled mutant has exactly three legal outcomes: **kill it** with a
>   test (prefer asserting the property it breaks over restating the
>   implementation), **refactor** it out of existence, or **accept it** with a
>   written reason in `config/pitest/README.md` **and a short family label on
>   the row itself** — refreshes seed new rows `# untriaged`, and triage means
>   replacing that label, so the baseline always says which rows are argued
>   and which are debt. Never run a baseline-update task just to make the build
>   pass.
> - **A mutant is a question, not a specification.** Before writing a killing
>   test, state the externally intended property and an oracle independent of the
>   current implementation: public contract, protocol specification, caller
>   invariant, reference implementation, or domain rule. If it contradicts current
>   behavior, first demonstrate the bug with a regression test that fails against
>   the unmutated code, then fix production; never add a passing assertion that
>   merely locks in the bug. At PR or handoff, report each nontrivial behavioral
>   cluster — not each mutant — as `Property: ... | Oracle: ... | Outcome: missing
>   assertion / production bug / accepted equivalent`. Test names and assertions
>   normally carry the durable property; comment only when the oracle or unusual
>   setup would otherwise be lost, and never embed PIT coordinates or line numbers.
> - Baseline keys are line-less (`class,method,mutator,STATUS`) — editing
>   above a mutated method churns nothing, and `# line` tags are review
>   metadata. A new mutant replacing a killed one at the same key can inherit
>   its acceptance, so treat a line-drift advisory whose written argument no
>   longer fits the code as that swap until shown otherwise. Use the installed
>   plugin's named writer tasks and heed their candidate previews; never hand-edit
>   record structure or provenance stamps. A PIT, PIT-plugin/tool-artifact,
>   ArcMutate-base, or certificate change uses `pitest<Suite>BaselineRebase`: it
>   preserves every old row, seeds new rows `# untriaged`, and stamps the reviewed
>   toolchain only after a successful fresh observation. Perform a schema
>   migration/rollback only with a fleet pin plan. A `[history]` report may check
>   the ratchet but cannot support adding, removing, or relabelling
>   accepted/timeout records; run `pitest<Suite> -PnoMutationHistory` first.
> - Consumer hardening notes contain only local ownership, measurements, acceptance
>   reasons, and provenance. `AGENTS.md` may carry this exact generated,
>   digest-pinned template plus those local facts, but no independently maintained
>   copy of plugin task semantics; use `hardeningHelp` and
>   `hardeningAgentTemplate` as the installed-version authorities.
> - **Iterate with `-PmutateOnly=<class-glob>`** while killing a cluster —
>   seconds instead of the full suite — then re-run unscoped with
>   `-PnoMutationHistory` before any record decision; the tooling refuses to let
>   a scoped report touch the baseline.
> - Identical baseline rows are sibling mutants of one compound condition and
>   the comparison is a multiset: never hand-dedupe. When one sibling
>   survives, the verify names the killed sibling's test — the survivor is
>   the opposite branch direction; triage it as its own mutant.
> - **A survivor contradicted by an existing oracle may be contaminated evidence.**
>   Open PIT's HTML **Covering tests** list, then compare the same scoped,
>   history-free population with and without isolation:
>   `-PmutateOnly=<class> -PnoMutationHistory`, then
>   `-PmutateOnly=<class> -PisolateMutants`. An isolation-only kill points
>   to state leaked between mutants — commonly a thread, executor, handler, or
>   static fixture whose cleanup an earlier assertion failure skipped. Put
>   teardown in `finally`/`try`-with-resources and rerun normally, history-free;
>   isolated execution is diagnostic evidence, never a baseline decision.
> - **Stubs and fixtures return distinguishable, non-default values.** A stub
>   returning null/0/""/true/empty makes the matching return-value mutant
>   equivalent by accident of the fixture — the clock non-zero-origin rule
>   generalized to every stubbed return.
> - **Copy-on-write clusters split by direction.** Assert immutability of
>   returned collections (`assertThrows(UnsupportedOperationException, ...)`)
>   at every size: the mutable-escape direction is a kill, not an acceptance;
>   only the content-equal siblings are family-accepted equivalents.
> - **Randomized tests use fixed seeds, and never sleep**: the ratchet needs
>   deterministic kills, and PIT re-runs the suite per mutant, so one real wait
>   costs minutes. Exploration belongs to the fuzz targets.
> - **Do not rely on PIT's timeout to detect a mutant.** `TIMED_OUT` counts as
>   detected and is not written to the baseline, and it is load-dependent — the
>   same mutant can report `SURVIVED` alone and `TIMED_OUT` under
>   `qualityGate`. Verify a baseline in both modes; union only rows observed to
>   flip, never every `TIMED_OUT` row.
> - **A new timed-out mutant is a reviewer-stop, not detection noise.** For
>   exactly these mutants the ratchet cannot see a weakened covering
>   assertion — a timeout keeps "detecting" whatever the test asserts — so
>   each suite's timeouts are an audited set, not a count:
>   `config/pitest/<suite>-timeouts.csv` holds line-less `class,method,mutator`
>   keys plus a comment category; `# line` tags are diagnostic metadata only. Only
>   `cause:liveness` is admissible watchdog detection after deterministic
>   seams/budgets are exhausted: the mutated path has no path-owned finite
>   completion guarantee. A fixture's emergency exit does not demote that
>   liveness loss to resource work; record the fixture bound in the README. If that
>   bound is the claimed deterministic oracle, compare it with PIT's
>   `duration × timeoutFactor + timeoutConst`: a bound that cannot fail first
>   contributes no cause evidence, so shorten it and re-observe history-free. A
>   later emergency ceiling may coexist with production liveness but cannot prove it.
>   A straight-line path with no loop, retry, lock, wait, blocking
>   call, or external completion dependency is not credible liveness evidence.
>   Before
>   admitting liveness, prove the mutated path receives the clock/budget the test
>   observes, and check for a synchronous state reader that can expose the defect
>   without waiting. A `TestClock` on a collaborator cannot observe a subject using
>   the system clock. Seeded
>   `cause:untriaged`, missing/unknown categories, finite `cause:resource`, and
>   `cause:harness` work are reviewer-stops. `cause:harness` is the explicit
>   non-certifying holding state for a demonstrated finite covering-path/watchdog
>   race; it never makes the timeout admissible. Resource behavior gets a
>   deterministic contract test/fix when promised, otherwise a stable `SURVIVED`
>   equivalence argument —
>   never silent timeout membership. Liveness authorizes valid `TIMED_OUT`
>   evidence only, never `MEMORY_ERROR`: if a non-advancing loop races the heap
>   against the watchdog, make every covering path fail deterministically without
>   relying on PIT test order, or refactor the manual progress mutation site out
>   while preserving the tested contract.
>   `config/pitest/README.md` still holds the
>   full structural cause per member. The verify warns on any timeout outside
>   the set — paste the printed row, classify it, then write the cause — and on
>   members matching no mutant. Membership and cause are key-level, so a liveness
>   token claims every sibling under that key. A key proven to mix liveness and
>   finite causes is not representable as an honest certifying row: split/refactor
>   it into distinct method keys or eliminate the ambiguous site, then re-observe
>   history-free. A source-line qualifier cannot fix the identity without making
>   formatting a release gate. Positive multiplicity drift prints all current
>   line-full candidates for review;
>   source-line movement itself never warns, fails, or requires re-anchoring. Adding
>   a method, moving imports, or reflowing an expression is not a hardening record
>   change. Strict workflows run the
>   committed-file half before PIT; use `pitest<Suite>Debt` for the same quick
>   manual preview. `TimeoutAuditInit` deliberately seeds an uncertifiable file —
>   classify every row before certification. For an otherwise admissible liveness
>   member, do not retire it until the tool emits its 3+ distinct fresh full-run quiet
>   notice over identical evidence inputs and the absence is confirmed under the
>   relevant solo/gate load. A finite KILLED↔TIMED_OUT race is benign only to baseline
>   arithmetic, never certifying evidence; repair/retime its covering path instead of
>   admitting it or waiting on the liveness-retirement rule. The quiet stash
>   is a machine-local nomination: never copy or merge it, and retain the row when a
>   same-input gate confirmation is unavailable. Assisted reports are
>   previews and do not
>   advance timeout status or quiet-run evidence.
> - **A flaky harness is worse than recorded debt.** If an interleaving or a
>   boundary cannot be made deterministic, accept the mutant with a written
>   reason rather than chasing it with sleeps or spin-waits.
> - **A suite's percentage is not a target.** An accepted mutant with a written
>   reason is finished work, not debt. Before trying to raise a number, check
>   whether the remainder is `NO_COVERAGE` (real work) or documented
>   equivalents (already closed).
> - **Allocation and timing harnesses are a last resort for thin constant-factor
>   differences**, reserved for properties that are a stated design goal. A
>   removed growth/capacity/amortisation guard that changes complexity class is
>   not “allocation-size only”: use a small input with an orders-of-magnitude
>   margin and the correct path through the mutated code. Harnesses re-run once
>   per mutant, need a `volatile` sink so escape analysis cannot delete what they
>   measure, and flap when the margin is thin.
> - When a test you believe in will not go green, **suspect the code before you
>   soften the assertion** — that is where this process finds real bugs.
> - **A wandering unkilled count is a defect, not noise** — chase it before
>   changing any baseline. Reproduce it under the relevant solo/gate loads,
>   inspect per-mutant coordinates, remove real waits, and move construction
>   coverage into the test body before deciding whether it is a product defect,
>   a load-dependent timeout, or a harness defect.
> - **Build the subject under test inside the test body, not in a field.**
>   Under `PER_CLASS` lifecycle a field-initialized client's construction
>   coverage attaches to whichever test runs first, so wiring mutants can
>   never pair with the test that drives what they wire — they survive even
>   under a harness that asserts every request. One test that constructs the
>   client in the test method and drives each configured URL restores the
>   pairing.
> - **Kill rates are bounded by the mutator set.** `BigInteger`/`BigDecimal`
>   arithmetic and receiver-returning fluent calls can be invisible to the
>   enabled defaults. Follow the plugin's trial advice per suite, enable only
>   mutators proved to fire, and record the measured numbers and declines.
> - Module-path and mutation-test service discovery can differ. Declare real
>   services in every runtime representation the project supports, probe the
>   active environment in test-only scaffolding, and never commit a harness
>   whose pass/fail result depends on which task launched it.
> - `SURVIVED` and `NO_COVERAGE` are different problems: the first is a
>   judgment call about equivalence, the second is usually an untested line
>   and is mechanical. Never accept a `NO_COVERAGE` mutant as "equivalent" —
>   you have not observed its behaviour. One structural exception: a block
>   that always exits by throw reads `NO_COVERAGE` forever, executed or not
>   (PIT probes a block at its end), and its return-value mutants can never
>   change status. Such a line is owed a test asserting the throw's contract,
>   not coverage — and never leave one untested fearing a covered-line
>   `SURVIVED` conversion, which would require the block to complete.
> - Exclusions must cover the **test source set**, not a naming convention:
>   shared fakes are named `RecordingFoo` / `StubFoo` and match no `*Test*`
>   pattern. After registering or widening a suite, list the mutated classes and
>   confirm none live under `src/test`.
> - **Verify by the absence of failures, not the presence of passes.** Counting
>   `PASSED` lines hides a failure sitting next to them, and a green
>   `clean build` can mean the build cache short-circuited rather than that
>   tests ran. Check the failure count and confirm the task actually executed.
>   A mutation run has a second version of this: a *failed* PIT run leaves the
>   previous run's report in place, so the summary you read can describe a run
>   that never happened. Trust the exit code, and delete report directories
>   when comparing runs.
> - **A suite that got faster without getting narrower is a bug report.** Real
>   speedups come from fewer mutants or faster covering tests; an unexplained
>   one usually means the run did less than you think. Read the task's evidence
>   markers and scope; only a fresh full certification may support a release.
>   The process itself needs no ArcMutate licence and applies to any Java package.
> - **Invalid execution outcomes are not results.** PIT `MINION_DIED` fails
>   before writing a report, so it cannot corrupt one — re-run the suite; a
>   Gradle-worker `EOFException` death is the same shape, and a per-mutant
>   `RUN_ERROR` often first observed in a multi-suite run is the same
>   shape smaller (load average itself proves nothing; the hardening parser refuses
>   the report rather than certifying PIT's detected score). The refusal and
>   `pitest<Suite>Debt` name every offending row; retain the coordinate before a
>   quiet re-run replaces the report. `RUN_ERROR` alone diagnoses neither load nor
>   memory and never justifies changing threads or heap; record load/RSS as context,
>   retry once quietly, and tune only when PIT explicitly diagnoses a process-resource
>   failure. A repeat at the same coordinate is not evidence
>   of load: investigate the mutated bytecode, its covering tests, and the tool failure.
>   The daemon log
>   (`~/.gradle/daemon/<version>/daemon-<pid>.out.log`) keeps a failed build's
>   full output even when the shell discarded it — read it before calling a
>   failure unexplained.
> - Fuzz findings become a committed seed input **and** a named regression
>   test, never just a fix — and the committed corpus is replayed by a unit
>   test inside `check`, so it cannot rot between fuzz runs.
> - **Run fuzz campaigns explicitly and locally.** `fuzzAll` is derived from every
>   registered target, so it cannot drift from a hand-written workflow task list;
>   set and record `-PmaxFuzzTime=<seconds>` and
>   `-PmaxParallelFuzzTargets=<count>` before release. Scheduled GitHub fuzz
>   workflows are optional and are not release evidence.
> - **When one thing has two representations, fuzz the differential.** Two
>   parsers for one config, an encode/decode round trip, a fast path beside a
>   reference path: assert the two *agree* rather than that neither crashes.
>   Crash-only fuzzing cannot see a wrong answer.
> - **Time-dependent code takes a clock**, so tests advance time instead of
>   waiting. Give test clocks a non-zero origin — a clock starting at 0 makes
>   every "start timestamp mutated to 0" mutant equivalent by accident.

### Build notes

- Gradle resolves the sava-build plugins from the Gradle Plugin Portal or GitHub
  Packages; GitHub Packages needs the `savaGithubPackagesUsername` /
  `savaGithubPackagesPassword` Gradle properties.
- The Java toolchain version comes from `gradle/sava.properties` (`javaVersion`); JDK
  provisioning is automatic.

#### Building against an unpublished sava-build change

To drive a sava-build feature or bug fix from this repo, publish sava-build to its local
test repo and point this build at it with `savaBuildLocalRepo` (the clone path belongs in
`AGENTS.local.md` or `~/.gradle/gradle.properties`, never in `settings.gradle.kts`):

```
(cd <sava-build> && ./gradlew publishSavaBuildTestPublicationToSavaTestRepoRepository) \
  && ./gradlew check -PsavaBuildLocalRepo=<sava-build>/build/sava-test-repo
```

The property adds that repo to `pluginManagement` and rewrites every `software.sava.build*`
plugin id to `software.sava:sava-build:0.0.0-test`, so the versions in the
`settings.gradle.kts` `plugins {}` block are ignored while it is set — nothing in that file
needs editing, and an unset property is the normal published path. Put it in
`~/.gradle/gradle.properties` to keep it on across invocations.

**The publish is not automatic.** sava-build's test repo is a plain Maven directory, not an
included build: every sava-build edit needs the publish task re-run, or this build silently
keeps resolving the previously published jar. (Gradle does re-read `file:` repositories on
each resolution, so a *republished* `0.0.0-test` is picked up immediately — the stale case
is only a forgotten publish, which is why the snippet above chains the two.) The tell is the
end-of-build notice the plugin prints whenever the property is set — it names the resolved
repo dir, the last-publish age and the application-time plugin SHA-256, on every build
including configuration-cache hits. When done, drop the property and bump the versions in
`settings.gradle.kts` to the released sava-build.

## AGENTS.local.md template

```markdown
# AGENTS.local.md

Machine-specific paths referenced by `AGENTS.md`. This file is git-ignored — keep local
paths here, never in `AGENTS.md`.

## Reference clones

- PagerDuty api-schema: /path/to/api-schema
- incident.io docs/schema: /path/to/incident-io-docs

## Related sava projects

- sava-build: /path/to/sava-build
- json-iterator: /path/to/json-iterator
```

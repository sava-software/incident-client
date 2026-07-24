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
`custom_details`) and `IncidentIoIncidentClient` (workspace-specific severity/type/status
ids are supplied at build time; resolve unsupported). When extending the common model,
update both adapters and their mapping tests.

## Canonical API references

The JSON shapes in this repo are hand-written; the upstream API schemas are the ground
truth. Verify against them, not against this repo's existing output:

- **PagerDuty** — `https://github.com/PagerDuty/api-schema`;
  `reference/events-v2/openapiv3.json` is the contract for `incident-pagerduty`
  (payload fields, severity enum, required fields, response shapes).
- **incident.io** — `https://api-docs.incident.io` (Create Incident V2) for
  `incident-io` request/response fields.

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
./gradlew check                                  # build + unit tests + baseline ratchet + template sync (all modules)
./gradlew qualityGate                            # pre-release: test + every pitest suite, serialized
./gradlew :<module>:pitest<Suite>                # PIT mutation testing, e.g.
                                                 #   :incident-core:pitestJson|pitestConfig|pitestApi
                                                 #   :incident-io:pitestRequest|pitestResponse|pitestConfig|pitestAdapter
                                                 #   :incident-pagerduty:pitestPayload|pitestResponse|pitestConfig|pitestAdapter
./gradlew :<module>:pitest<Suite>Debt            # unkilled mutants by class, with baseline delta
./gradlew :<module>:fuzz<Target> -PmaxFuzzTime=60  # Jazzer fuzzing, e.g.
                                                 #   :incident-io:fuzzRequest|fuzzResponse
                                                 #   :incident-pagerduty:fuzzPayload
./gradlew :<module>:fuzz<Target>Minimize         # corpus dedup (libFuzzer -merge); -PadoptLocalCorpus folds in local finds
```

Suites are declared in each module's `build.gradle.kts` under the `hardening` extension
(from sava-build's `software.sava.build.feature.hardening` plugin). The full process
doctrine lives in sava-build's `HARDENING.md` (local clone path in `AGENTS.local.md`) —
read it before triaging survivors or touching a baseline.

Each `pitest<Suite>` run diffs its unkilled mutants against the checked-in baseline at
`<module>/config/pitest/<suite>-accepted.csv` and fails on anything new; acceptance
reasons live in `<module>/config/pitest/README.md`. Baseline flags (mutually
exclusive): `-PupdateMutationBaseline` (full refresh; seeds genuinely new rows
`# untriaged`), `-PunionMutationBaseline` (append-only, for observed status flips),
`-PpruneMutationBaseline` (drop-only, always safe after a killing pass). While
iterating on one class, scope with `-PmutateOnly=<class-glob>` — scoped reports are
stamped and refused by every baseline-touching consumer. `-PlistUnkilled` prints
unkilled rows with PIT's mutation descriptions and, for a survivor whose
same-coordinate sibling was detected, the sibling's killing test — the survivor is
the opposite operand or branch direction of whatever that test pinned. Triage
replaces a row's `# untriaged` label with a family label; the verify counts rows
per label and warns when a label has no matching `# <label>` argument in
`config/pitest/README.md`.

**`qualityGate` ownership:** CI (the shared sava-build workflow) runs `check` only;
the pre-release `qualityGate` plus long fuzz runs are release-checklist items run
locally.

Conventions to preserve:

- The hardening recompile merges main **and test** sources into one classpath root, so
  mutation suites that use package globs must set `excludedClasses = listOf("*Test*")`
  or PIT will mutate the tests themselves and report meaningless survivors.
- Fuzz targets live in test sources, are deliberately free of Jazzer imports (a public
  static `fuzzerTestOneInput(byte[])`), and are **round-trip oracles**: build with
  arbitrary strings, serialize, assert no raw control characters survive, re-parse with
  `JsonIterator`, and compare against the documented transform. When you add or change a
  serialized field, extend the fuzz oracle *and* add an exact-string unit test — the
  mutation suites only measure what the unit tests pin down.
- Parser fuzz targets follow the "garbage in → RuntimeException out" contract. Seed
  corpora live under `src/test/resources/fuzz/<target>/`; every target with a
  `seedCorpus` gets a generated `<Harness>SeedReplayTest` that replays the corpus
  inside `test` (and under PIT), and fails on an empty corpus. Committed seeds must
  not exceed the target's `maxLen` — the fuzz and minimize tasks refuse up front
  rather than let libFuzzer silently truncate the seed.
- Copy-on-write builder survivors (`size() > 1 ? copy : as-is`) split by
  direction: the content-equal siblings are accepted `# copy-on-write` /
  `# defensive-copy` equivalents, but the mutable-escape direction is killed
  by the immutability tests (`assertThrows(UnsupportedOperationException,
  ...)` on record collections) — keep those assertions when touching the
  builders.

### Hardening rules (synced from sava-build's HARDENING.md)

The block below is a snapshot of sava-build's agent-instructions template; the marker
acknowledges the template digest and is checked by `agentsTemplateInSync` (wired into
`check`). When the check fails after a sava-build upgrade, re-diff this block against
the template, sync or **act on** each changed bullet, then update the marker to the
digest the failure message prints.

<!-- hardening-template sha256:7f9eb869ee7e -->

> - **Scale verification to the change.** Iterate with the module's `test`
>   task; before handing off, run only the `pitest<Suite>`(s) whose mutated
>   code the change can reach — including suites in dependent modules that
>   call a changed API, and the owning suite for test-only edits (a weakened
>   test is exactly what the ratchet catches). The full `qualityGate` — every
>   suite, serialized, diffed against `config/pitest/` — is the pre-release
>   check, owned by CI or by the release checklist (this repo records which);
>   it is not the inner loop.
> - A new unkilled mutant has exactly three legal outcomes: **kill it** with a
>   test (prefer asserting the property it breaks over restating the
>   implementation), **refactor** it out of existence, or **accept it** with a
>   written reason in `config/pitest/README.md` **and a short family label on
>   the row itself** — refreshes seed new rows `# untriaged`, and triage means
>   replacing that label, so the baseline always says which rows are argued
>   and which are debt. Never run `-PupdateMutationBaseline` just to make the
>   build pass.
> - Pure line drift — every new baseline entry a same-status shift of a stale
>   one, populations unchanged — passes on its own with a notice; refresh at a
>   convenient moment. Anything mixed in (newly covered, unexplained, changed
>   counts) still fails and is triage first, refresh after.
> - **Iterate with `-PmutateOnly=<class-glob>`** while killing a cluster —
>   seconds instead of the full suite — then re-run unscoped before any
>   refresh; the tooling refuses to let a scoped report touch the baseline.
> - Identical baseline rows are sibling mutants of one compound condition and
>   the comparison is a multiset: never hand-dedupe. When one sibling
>   survives, the verify names the killed sibling's test — the survivor is
>   the opposite branch direction; triage it as its own mutant.
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
> - **A flaky harness is worse than recorded debt.** If an interleaving or a
>   boundary cannot be made deterministic, accept the mutant with a written
>   reason rather than chasing it with sleeps or spin-waits.
> - **A suite's percentage is not a target.** An accepted mutant with a written
>   reason is finished work, not debt. Before trying to raise a number, check
>   whether the remainder is `NO_COVERAGE` (real work) or documented
>   equivalents (already closed).
> - **Allocation and timing harnesses are a last resort**, reserved for
>   properties that are a stated design goal. They re-run once per mutant, need
>   a `volatile` sink so escape analysis cannot delete what they measure, and
>   flap when the margin is thin.
> - When a test you believe in will not go green, **suspect the code before you
>   soften the assertion** — that is where this process finds real bugs.
> - **A wandering unkilled count is a defect, not noise** — chase it before
>   refreshing any baseline. Known causes: real waits, `TIMED_OUT` load flips,
>   `@Execution`/`@TestInstance` not reaching concrete test classes from an
>   abstract base (version-dependent — JUnit 6 marks both `@Inherited`; check
>   the resolved jar), and coverage attributed to field initializers —
>   exercise factories from inside a `@Test`.
> - **Build the subject under test inside the test body, not in a field.**
>   Under `PER_CLASS` lifecycle a field-initialized client's construction
>   coverage attaches to whichever test runs first, so wiring mutants can
>   never pair with the test that drives what they wire — they survive even
>   under a harness that asserts every request. One test that constructs the
>   client in the test method and drives each configured URL restores the
>   pairing.
> - **Kill rates are bounded by the mutator set.** `BigInteger`/`BigDecimal`
>   arithmetic is method calls, invisible to the default arithmetic mutators —
>   fixed-point and fee math needs `EXPERIMENTAL_BIG_INTEGER` (pitest ≥
>   1.25.8) — and fluent calls returning their receiver are expressions,
>   invisible to `VoidMethodCallMutator` — builder-style writes need
>   `EXPERIMENTAL_NAKED_RECEIVER`. Trial per suite, enable only what fires,
>   and record the numbers.
> - **PIT minions run on the class path**, even in module-path repos:
>   `module-info` services are invisible to them, and a test-resources
>   `META-INF/services` is invisible to the module-path `test` task. Real
>   services are declared in both places; a harness whose result depends on
>   which task ran it is never committed.
> - `SURVIVED` and `NO_COVERAGE` are different problems: the first is a
>   judgment call about equivalence, the second is an untested line and is
>   mechanical. Never accept a `NO_COVERAGE` mutant as "equivalent" — you have
>   not observed its behaviour.
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
>   one usually means the run did less than you think. Exception: a summary
>   carrying the `[history]` marker is arcmutate incremental reuse and fast is
>   expected — but the pre-release gate still runs with `-PnoMutationHistory`
>   to re-earn every status from scratch.
> - **Transient infra failures are not results.** PIT `MINION_DIED` fails
>   before writing a report, so it cannot corrupt one — re-run the suite; a
>   Gradle-worker `EOFException` death is the same shape, and a per-mutant
>   `RUN_ERROR` under load is the same shape smaller (the summary names it,
>   and it is not counted as detected). The daemon log
>   (`~/.gradle/daemon/<version>/daemon-<pid>.out.log`) keeps a failed build's
>   full output even when the shell discarded it — read it before calling a
>   failure unexplained.
> - Fuzz findings become a committed seed input **and** a named regression
>   test, never just a fix — and the committed corpus is replayed by a unit
>   test inside `check`, so it cannot rot between fuzz runs.
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

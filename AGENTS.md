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
  `dedup_key` over 255 chars is refused by the trigger payload builder (the native
  `PagerDutyEventClient`'s and `PagerDutyService`'s acknowledge and resolve send the key as
  given); through `IncidentClient` it never reaches the builder:
  the client and `IncidentService` both shorten a key to the client's declared `maxKeyLength`
  (255 for PagerDuty, none for incident.io and the webhook formats) through
  `IncidentClient.boundKey`, the same way on every report and resolve: the key's first
  characters, a colon and the SHA-256 of the whole key, within the limit in characters as
  Java counts them (never fewer than the Unicode characters PagerDuty's 255 counts).
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
./gradlew :<module>:hardeningHelp                # installed-version authority; run it
```

Those fifteen suites and four fuzz targets are this repo's inventory, declared in each
module's `build.gradle.kts` under the `hardening` extension (from sava-build's
`software.sava.build.feature.hardening` plugin). Everything else about the tooling — what
each writer task does, which properties exist, which have been refused — comes from
`:<module>:hardeningHelp` and `:<module>:hardeningAgentTemplate`, the installed-version
authorities; the
process doctrine behind them lives in sava-build's `HARDENING.md` (local clone path in
`AGENTS.local.md`).

Local hardening records, per module under `config/pitest/`: accepted baselines
`<suite>-accepted.csv`, their acceptance arguments in `README.md`, and their bound
toolchain provenance `<suite>-pitest-version` plus `<suite>-pitest-toolchain.tsv`. Ten
accepted files are tracked here: `incident-core` api; `incident-io` adapter, config and
request; `incident-pagerduty` adapter, config and payload; `incident-webhook` adapter,
config and format. No timeout records are tracked in this repository today.

**Ownership:** CI runs `check` only. `:<module>:hardeningCertify` plus an explicit local
`:<module>:fuzzAll -PmaxFuzzTime=<seconds> -PmaxParallelFuzzTargets=<count>` campaign are
release-checklist items, run locally with both budgets recorded. Run
`:<module>:mutationOwnershipAudit` with the pre-push gate, once per reviewed unpushed
range, whenever the production-class inventory changes or a suite's target/exclusion rules
change.

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
  format from scratch" answers only the bootstrap question. All four targets here
  declare one; if a future target genuinely does neither job, take the decline property
  from `:<module>:hardeningHelp`, and record the measurement
  that justified it the way an acceptance is argued. Seed
  provenance goes in a README next to — never inside — the corpus dir.
- Our copy-on-write builder acceptances (`# copy-on-write` / `# defensive-copy` in
  `api-accepted.csv` and `payload-accepted.csv`) argue only the content-equal sibling of
  each `size() > 1 ? copy : as-is` ternary; the mutable-escape direction is killed by the
  record-collection immutability tests (`assertThrows(UnsupportedOperationException, ...)`)
  — keep those assertions when touching the builders.

### Hardening rules (synced from sava-build's HARDENING.md)

The block below is the one `:incident-core:hardeningAgentTemplate` prints for the sava-build
version pinned in `settings.gradle.kts`. After a sava-build upgrade, re-take it whole from that
task, between and including its two boundary comments, **act on** each changed bullet, and land
it with the pin, never ahead of it. Nothing checks the copy: a stale block costs a wrong
instruction, not a red build.

<!-- hardening-template block:start -->
- Work with the module's `test` task. The mutation suites are a final gate, run once per
  unpushed range when the work is complete and reviewed, before the push: each
  `pitest<Suite>` whose mutated code the range can reach, including suites in dependent
  modules, plus `mutationOwnershipAudit` when production classes or target/exclusion
  rules changed. Never per commit, amend or review round; a change the gate forces goes
  back through review as a delta. `hardeningCertify` (or `:hardeningCertifyAll`) and
  `fuzzAll` are the pre-release checks this repo's notes assign an owner to.
- Test-only edits owe the suites whose mutated code those tests exercise; when scope
  is unclear, run `./gradlew qualityGate` from the Gradle root. Doc and comment
  edits owe no suite; a build-script edit only when it changes what
  PIT is given. A change the gate forced owes it again by the same reachability rule
  once reviewed. `pitest<Suite>Verify` answers one way: it keeps its report while only
  recompiled Java sources changed and every recompiled class is byte-identical, which
  proves that suite is owed nothing; a refusal (a moved line, a resource, a build
  script, `src/main/java` under ArcMutate) names its cause and proves nothing by itself.
- When the gate reports unkilled mutants, iterate on one cluster with
  `-PmutateOnly=<class-glob>`. Before any record decision, re-run unscoped with
  `-PnoMutationHistory`: a `[history]` report cannot support adding, removing, or
  relabelling records.
- An unkilled mutant has three outcomes: kill it with a test that asserts the
  property it breaks, refactor it out of existence, or accept it with a written
  reason in `config/pitest/README.md` and a family label on the row. Refreshes seed
  rows `# untriaged`; triage replaces that label. Never accept a `NO_COVERAGE`
  mutant as equivalent; it is an untested line.
- A mutant is a question, not a specification. State the intended property and an
  oracle independent of the implementation before writing the killing test. If they
  contradict current behaviour, prove the bug with a failing regression test first,
  then fix production; never lock a bug in with a passing assertion.
- Write records only through the installed writer tasks: `BaselineUnion` adds reviewed
  rows, `BaselineRetag` refreshes `# line` metadata, `BaselinePrune` deletes only after
  two matching fresh history-free previews, `BaselineUpdate` is for a first seed or a
  reviewed complete rewrite, and `pitest<Suite>BaselineRebase` follows a PIT,
  PIT-plugin/tool-artifact, ArcMutate-base, or certificate change. Never hand-edit
  baseline rows or provenance stamps.
- Baseline keys are line-less (`class,method,mutator,STATUS`); `# line` tags are
  review metadata that belong to their row: `BaselineRetag` refreshes them, a hand
  edit is a hand-edited row. Identical rows are sibling mutants and the comparison
  is a multiset: never hand-dedupe.
- `config/pitest/README.md` holds the arguments in force, each updated in place and
  never appended to as a pass report: a family's members, reason, oracle and the
  condition that invalidates it; an audited timeout's cause. The totals the build prints
  are not restated there; the measurements it cannot reconstruct are kept.
- A new `TIMED_OUT` mutant is a reviewer stop, never detection. Record it in
  `config/pitest/<suite>-timeouts.csv` with a cause and argue it in the README; only
  `cause:liveness` certifies. A member whose coordinate has left the population is
  removed by hand after one fresh history-free run with valid committed provenance
  omits it; while provenance is invalid, repair or rebase it first.
- Tests are deterministic: fixed seeds, no sleeps, a clock with a non-zero origin,
  stubs that return distinguishable non-default values, and the subject built inside
  the test body. Exclusions must cover the test source set, not a naming convention.
- Git-ignored scratch sources (`Integ.java` drivers) never sit in a hardened project's
  `main` or `test` source set; keep them in a git-ignored `scratch` source set instead.
- Verify by the absence of failures: trust the exit code and the `.running`
  sentinel, not a summary. `MINION_DIED` and `RUN_ERROR` are not results; re-run once
  on a quiet machine. A suite that got faster without getting narrower is a bug report.
- Fuzz findings become a committed seed input and a named regression test. Run
  `fuzzAll` locally with an explicit `-PmaxFuzzTime` and `-PmaxParallelFuzzTargets`
  before a release. Where one thing has two representations, fuzz the differential.
- `./gradlew :module:hardeningHelp` lists the installed tasks and options;
  sava-build's HARDENING.md holds the argument behind every rule above.
<!-- hardening-template block:end -->

### Build notes

- Gradle resolves the sava-build plugins from the Gradle Plugin Portal or GitHub
  Packages; GitHub Packages needs the `savaGithubPackagesUsername` /
  `savaGithubPackagesPassword` Gradle properties.
- This repo pins its toolchain with `javaVersion` in `gradle/sava.properties`, and
  `settings.gradle.kts` applies sava-build's `software.sava.build.feature.jdk-provisioning`
  settings plugin; how that plugin resolves or provisions a JDK is the plugin's to
  document.

#### Building against an unpublished sava-build change

To drive a sava-build feature or bug fix from this repo, publish sava-build to its local
test repo and point this build at it with `savaBuildLocalRepo` (the clone path belongs in
`AGENTS.local.md` or `~/.gradle/gradle.properties`, never in `settings.gradle.kts`):

```
(cd <sava-build> && ./gradlew publishSavaBuildTestPublicationToSavaTestRepoRepository) \
  && ./gradlew check -PsavaBuildLocalRepo=<sava-build>/build/sava-test-repo
```

Run `:<module>:hardeningHelp` for the installed-version account of the
`savaBuildLocalRepo` property. The repository-specific rule is only
where the value lives: `AGENTS.local.md` or `~/.gradle/gradle.properties` to keep it on
across invocations, never `settings.gradle.kts`.

**Re-publish before every run.** This repo's policy is to chain the publish task and the
build after each sava-build edit, as the snippet above does. Consult
`:<module>:hardeningHelp` rather than a copy kept here. When done, drop the property and
bump the versions in `settings.gradle.kts` to the released sava-build.

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

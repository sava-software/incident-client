# Mutation-testing baseline & triage policy

`<suite>-accepted.csv` records this module's argued-unkilled mutants. Each row is
`class,method,mutator,STATUS` plus a trailing `# <family>` label and a diagnostic
`# line N` tag; identical rows are sibling mutants of one compound condition and the
comparison is a multiset, so never hand-dedupe. `<suite>-pitest-version` and
`<suite>-pitest-toolchain.tsv` bind the PIT/ArcMutate toolchain the rows were measured
under. A suite with nothing unkilled has no accepted file at all.

The legal outcomes for a new unkilled mutant, the determinism rules, and the named writer
tasks that may touch these files are all owned by sava-build: run
`./gradlew :<module>:hardeningHelp` and `./gradlew :<module>:hardeningAgentTemplate` for the
installed version's authority, and read sava-build's `HARDENING.md` for the doctrine behind
them. Never widen a baseline just to make a build pass.

`# untriaged` marks recorded-but-not-yet-argued debt — it is not acceptance, and a
`NO_COVERAGE` row is never an equivalence claim. Everything below the triaged heading is
grouped by the principle that makes it equivalent.

## Untriaged debt

A first baseline seeded from the pre-existing survivor population is triage
debt made explicit, not acceptance. List it here until each key is killed,
refactored away, or moved below with a reason.

- None. The 2026-07-24 initial seed (`config` 3, `api` 26, `json` clean) was
  triaged 2026-07-24: 21 killed with new tests (retry-delay arithmetic,
  give-up-after call counts, checked-exception propagation, transport-failure
  retries, blank/absent config values, and JUL-captured failure logging via
  the generated `JulRecorder`), 8 accepted below.
- None for `client`. The suite was added 2026-08-06 when `mutationOwnershipAudit`
  showed `core.client.*` and `core.request.*` had no mutation-suite owner: the
  exported transport/request builder bases are extended only by provider clients in
  other Gradle modules, so no kill could ever land in this one. All 19 mutants were
  killed on the first run by `HttpApiClientBuilderTests` and `RequestTests`, which
  pin fluent-setter identity, accessor round-trips, `endpoint(String)` URI parsing,
  and that `setDefaults()` fills only the unset transport values — an overwriting
  defaults hook would silently discard a caller's `HttpClient`.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# copy-on-write` (api: `IncidentAlertBuilder.create:56` ×3,
  `customDetail:103`) — single-vs-multi entry routing in the copy-on-write
  builder map: both branches produce an unmodifiable map with identical
  content. The insertion-order contract the `size() > 1` branch preserves is
  only violated by `Map.copyOf`'s salted iteration order, which varies per
  JVM launch — a test asserting order would kill the mutant only
  probabilistically, and a flaky kill is worse than recorded debt.
  Escape hatch: a deterministic-order `Map.copyOf` (or a JDK flag pinning
  the salt) would make the order assertable.
- `# defensive-copy` (api: `IncidentAlertBuilder.<init>:39`) — the
  `isEmpty()` branch of the prototype constructor: an empty non-null
  prototype map is copied into a `LinkedHashMap` instead of `Map.of()`.
  Content-equal; distinguishable only by mutating the builder's internal
  map through the accessor, which the API does not invite. Equal but not
  identical.
- `# service-loader-binding` (api: `IncidentClients.createClient:33,37,41`,
  `loadFactory:47`) — **the structural `NO_COVERAGE` exception, re-argued
  2026-08-06; not an equivalence claim.** These are the `NullReturnValsMutator`
  mutants on the four public one-line wrappers that bind
  `ServiceLoader.load(IncidentClientFactory.class)` and delegate to the
  package-private registry seams. No provider module is on incident-core's test
  path, so `loadFactory` always throws here and every one of these blocks exits by
  throw. PIT probes a block at its end, so the block never completes: the rows read
  `NO_COVERAGE` whether or not the wrapper ran, and their return-value mutants can
  never change status. Doctrine's remedy for such a line is a test asserting the
  **throw's** contract, not coverage, and each wrapper has one — `createClient:33`
  by `missingProvider` and `blankProvider`, `createClient:37` by
  `prefixedProviderLookup`, `createClient:41` by `jsonProviderMustPrecedeConfig`
  and `jsonUnknownField`, `loadFactory:47` by `unknownProvider` (which pins the
  exact "No IncidentClientFactory found for provider" message). The resolution and
  dispatch logic behind the wrappers is pinned through the seams with
  `IncidentClientsTests`' stub factories, and the wrappers run end-to-end against
  real `ServiceLoader` registration in the provider modules'
  `PagerDutyIncidentClientFactoryTests` / `IncidentIoIncidentClientFactoryTests` —
  kills that land outside this suite's `targetTests`. Registering a test-only
  provider here would not convert these rows: it would move the throw, not make the
  block complete, and a harness whose result depended on whether the module-path
  `test` task or PIT's classpath minion discovered it is exactly what doctrine
  forbids committing.
- `# async-routing` (api: `IncidentServiceVal.retry:71` ×3) — `retryDelay >
  0` routes between `exceptionallyComposeAsync(delayedExecutor(...))` and
  `exceptionallyCompose`: at delay 0 both produce identical results and
  differ only in which thread runs the continuation; a positive delay is
  distinguishable only by wall-clock timing, which the determinism rules
  prohibit asserting. Escape hatch: a clock seam injected into the service
  (replacing `delayedExecutor`) would make the routing assertable.

Shrinking a baseline is always an improvement; growing one requires a reason
here.

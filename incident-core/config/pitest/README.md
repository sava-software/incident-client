# Mutation-testing baseline & triage policy

`<suite>-accepted.csv` records this module's argued-unkilled mutants. The CSV's
`# line N` tag and the current PIT report are the only places a source coordinate
belongs — this README identifies a mutant by class, method, mutator, and the branch
being argued, never by line. `<suite>-pitest-version` and
`<suite>-pitest-toolchain.tsv` are committed beside them. This module currently
keeps a baseline for the `api` suite only.

The legal outcomes for a new unkilled mutant, the determinism rules, how a baseline is
compared, and the named writer tasks that may touch these files are all owned by
sava-build rather than by this README: run `./gradlew :incident-core:hardeningHelp` and
`./gradlew :incident-core:hardeningAgentTemplate` for the installed version's authority,
and read sava-build's `HARDENING.md` for the doctrine behind them. Never widen a
baseline just to make a build pass.

Everything below the triaged heading is
grouped by the principle that makes it equivalent. The current PIT report and each row's
`# line N` tag are where a reviewer picks up source coordinates; this README does not
carry them.

## Untriaged debt

- None. The 2026-07-24 initial seed (`config` 3, `api` 26, `json` clean) was
  triaged 2026-07-24: 21 killed with new tests (retry-delay arithmetic,
  give-up-after call counts, checked-exception propagation, transport-failure
  retries, blank/absent config values, and JUL-captured failure logging via
  the generated `JulRecorder`), 8 accepted below.
- None for `client`. The suite was added 2026-08-06, when `core.client.*` and
  `core.request.*` were found to have no mutation-suite owner — this repo runs
  `:incident-core:mutationOwnershipAudit` before handoff. The exported
  transport/request builder bases are extended only by provider clients in other
  Gradle modules, so no kill could ever land in this one. All 19 mutants were
  killed on the first run by `HttpApiClientBuilderTests` and `RequestTests`, which
  pin fluent-setter identity, accessor round-trips, `endpoint(String)` URI parsing,
  and that `setDefaults()` fills only the unset transport values — an overwriting
  defaults hook would silently discard a caller's `HttpClient`.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# copy-on-write` (api: `IncidentAlertBuilder.create` ×3 —
  `ConditionalsBoundaryMutator` plus both `RemoveConditionalMutator_ORDER_*`;
  `IncidentAlertBuilder.customDetail` ×1 — `RemoveConditionalMutator_EQUAL_IF`) —
  single-vs-multi entry routing in the copy-on-write builder map: `create`'s
  `customDetails.size() > 1` ternary and `customDetail`'s `size() == 1`
  copy-escalation. Both branches produce an unmodifiable map with identical
  content. The insertion-order contract the `size() > 1` branch preserves is
  only violated by `Map.copyOf`'s salted iteration order, which varies per
  JVM launch — a test asserting order would kill the mutant only
  probabilistically, and a flaky kill is worse than recorded debt.
  Escape hatch: a deterministic-order `Map.copyOf` (or a JDK flag pinning
  the salt) would make the order assertable.
- `# defensive-copy` (api: `IncidentAlertBuilder.<init>` ×1 —
  `RemoveConditionalMutator_EQUAL_ELSE`) — the `isEmpty()` branch of the
  prototype constructor: an empty non-null prototype map is copied into a
  `LinkedHashMap` instead of `Map.of()`. Content-equal; distinguishable only
  by mutating the builder's internal map through the accessor, which the API
  does not invite. Equal but not identical.
- `# service-loader-binding` (api: `IncidentClients.createClient` ×3,
  `IncidentClients.loadFactory` ×1 — `NullReturnValsMutator`) — **the structural
  `NO_COVERAGE` exception, re-argued 2026-08-06; not an equivalence claim.** These
  are the `NullReturnValsMutator` mutants on the four public one-line wrappers that
  bind `ServiceLoader.load(IncidentClientFactory.class)` and delegate to the
  package-private registry seams. No provider module is on incident-core's test
  path, so `loadFactory` always throws here and every one of these blocks exits by
  throw. Each wrapper's throw contract is asserted — the prefix-less
  `createClient(Properties)` overload by `missingProvider` and `blankProvider`, the
  prefixed `createClient(Properties, String)` overload by `prefixedProviderLookup`,
  the `createClient(JsonIterator)` overload by `jsonProviderMustPrecedeConfig`
  and `jsonUnknownField`, and `loadFactory(String)` by `unknownProvider` (which pins
  the exact "No IncidentClientFactory found for provider" message). The resolution and
  dispatch logic behind the wrappers is pinned through the seams with
  `IncidentClientsTests`' stub factories, and the wrappers run end-to-end against
  real `ServiceLoader` registration in the provider modules'
  `PagerDutyIncidentClientFactoryTests` / `IncidentIoIncidentClientFactoryTests` —
  kills that land outside this suite's `targetTests`. Registering a test-only
  provider here would not convert these rows: it would move the throw, not make the
  block complete, and a harness whose result depended on whether the module-path
  `test` task or PIT's classpath minion discovered it is exactly what doctrine
  forbids committing.
- `# async-routing` (api: `IncidentServiceVal.retry` ×3 —
  `ConditionalsBoundaryMutator` plus both `RemoveConditionalMutator_ORDER_*`) — the
  `retryDelay > 0` guard routes between
  `exceptionallyComposeAsync(delayedExecutor(...))` and `exceptionallyCompose`: at
  delay 0 both produce identical results and differ only in which thread runs the
  continuation; a positive delay is distinguishable only by wall-clock timing, which
  the determinism rules prohibit asserting. Escape hatch: a clock seam injected into
  the service (replacing `delayedExecutor`) would make the routing assertable.

Growing a baseline requires a reason here.

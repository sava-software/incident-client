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

- None. The 2026-07-24 initial seed (`payload` 47, `config` 4, `adapter` 4,
  `response` clean) was triaged 2026-07-24: 30 killed with new tests
  (single-entry prototype-copy immutability, chained link/image/custom-detail
  adds, null-collection prototypes, typed custom-detail overloads, timestamp
  and dedup-key defaults, blank-vs-null branches of every optional field,
  sparse-config builder preservation, adapter delegation) or removed by
  refactor (dead null-guard in `customDetailsObject` — both constructors
  initialize the map), 20 accepted below. `response` and `adapter` are clean.
- **`adapter`, 130 rows, seeded 2026-08-06 by `mutationOwnershipAudit` adoption.** The
  `adapter` suite previously named three classes; 22 of this module's production classes
  had no mutation-suite owner at all. Widening the suite to `event.client.*`,
  `event.service.*` and `exceptions.*` made a pre-existing hole visible — this is not new
  debt, and none of it is an equivalence claim. By class:
  - `event.service.PagerDutyServiceVal` 86 and `PagerDutyService` 21 and
    `PagerDutyServiceBuilder` 3, all `NO_COVERAGE`: the whole `event.service` package is
    untested. It is PagerDuty's retrying wrapper — the same shape as
    `incident-core`'s `IncidentServiceVal`, which *is* covered by `IncidentServiceTests`.
    Closing it means porting that test shape (retry-delay arithmetic, give-up-after call
    counts, checked-exception propagation, transport-failure retries) onto
    `PagerDutyService`.
  - `exceptions.PagerDutyParseException` 5 `NO_COVERAGE` + 4 `SURVIVED`,
    `PagerDutyRequestException` 2 + 1, its builder 1 + 2: this module has no exceptions
    test, where `incident-io` and `incident-webhook` both do. Closing it means the
    accessor / `canBeRetried` boundary / parse-failure-message tests those modules have.
  - `event.client.PagerDutyEventClient$Builder` 3 `NO_COVERAGE` + 2 `SURVIVED`: the
    unobserved accessor and header-composition branches of the client builder.

  One further observation worth keeping: the first widened multi-suite run reported a
  `RUN_ERROR` at `PagerDutyEventClient.asIncidentClient:20`
  (`NullReturnValsMutator`). A quiet history-free re-run — both scoped to that class and
  as the full suite — killed it, so it was an invalid execution outcome under multi-suite
  load, not a defect at that coordinate. Recorded here because the plugin refuses any
  report containing one, so a repeat must be investigated rather than re-run away.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# copy-on-write` (payload: both builders' `create` size ternaries ×12,
  `customDetailsObject:145`, `link:177`, `image:189`) — single-vs-multi
  routing in the copy-on-write collections. The `create` wrappers only ever
  wrap collections that are already immutable at size ≤ 1 (`List.of` /
  `Map.copyOf`), so `unmodifiable*` vs as-is is indistinguishable; the
  size==1 setter branches only re-copy content-equal maps/lists. The
  mutable-escape direction (`ORDER_ELSE`, multi-entry as-is) is killed by
  the immutability tests — these are the content-equal siblings.
- `# defensive-copy` (payload: prototype constructor `<init>:82/88/94`) —
  the `isEmpty()` branch of each prototype-copy ternary: an empty non-null
  prototype collection is routed to the size-check chain and lands on
  `Map.copyOf`/`List.copyOf` of an empty collection — content-equal to the
  `Map.of()`/`List.of()` the guard returns. Equal but not identical.
- `# truncation-boundary` (payload: `summary:122`) — `length() > 1_024`
  mutated to `>=`: at exactly 1024 chars `substring(0, 1024)` returns the
  same instance (`summaryExactly1024IsNotCopied` pins the identity), so the
  boundary flip is unobservable by construction.
- `# status-boundary` (adapter: `PagerDutyEventClientImpl` response parser,
  the `code < 200` arm of the success gate) — the forced-true direction is
  observable only with a final HTTP status below 200, which
  `java.net.http.HttpClient` cannot deliver: 1xx interim responses are
  consumed inside the client, and `jdk.httpserver` refuses to emit them as
  final. Unreachable in-harness. Escape hatch: a raw socket speaking
  HTTP/1.1 by hand (sava-build's `LoopbackHttpServer`) could write a
  literal sub-200 final status line, at the cost of the client blocking
  until its request timeout.
- `# always-true-delegate` (config: `PagerDutyConfig$Parser.test:99`) —
  `return super.test(...)` where the superclass either returns true or
  throws on unknown fields; the constant-true mutant preserves the call and
  its side effects. Mirrors the same acceptance in incident-io.

Shrinking a baseline is always an improvement; growing one requires a reason
here.

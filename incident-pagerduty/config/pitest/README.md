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
- None for `adapter`. `mutationOwnershipAudit` adoption on 2026-08-06 exposed 130 rows: the
  suite had named three classes, leaving 22 of this module's production classes with no
  owner at all. Widening it to `event.client.*`, `event.service.*` and `exceptions.*` made
  a pre-existing hole visible, and all of it was worked down 2026-08-07 (221 mutants,
  90 → 213 detected). What closed it:
  - `PagerDutyServiceTests` covers the whole `event.service` package, which had no test:
    110 rows across `PagerDutyServiceVal`, `PagerDutyService` and `PagerDutyServiceBuilder`
    reduced to the 6 accepted below. It ports the shape of `incident-core`'s
    `IncidentServiceTests` — retry-delay arithmetic and its give-up boundary, give-up-after
    deriving retries by dividing the budget by the step delay, the three throwable shapes
    each failure handler must propagate, `canBeRetried` with the client exception both as
    the failure and as its cause, and the JUL-captured failure logging. Two traps worth
    remembering: the `(…, long stepDelay, long maxDelay, TimeUnit)` overloads are distinct
    methods from the `(…, int maxRetries, …)` ones and need their own calls, and a null
    cause must be wrapped around the throwable itself rather than becoming a null cause.
  - `PagerDutyExceptionTests` covers `exceptions.*`, which had no test where `incident-io`
    and `incident-webhook` both did: the `canBeRetried` 5xx/429 classification and its
    428/430/499 boundaries, every constructor's response/cause/buffer wiring, the error
    list growing none → one → many with immutability above one, and `toString`.
  - `PagerDutyEventClientBuilderTests` covers the client builder's accessors and the
    branch that chooses *which* request decorator gets installed: absent, blank and
    present auth tokens, and a caller-supplied `extendRequest` that must be left alone.

  One observation worth keeping: the first widened multi-suite run reported a `RUN_ERROR` at
  `PagerDutyEventClient.asIncidentClient:20` (`NullReturnValsMutator`). A quiet history-free
  re-run — both scoped to that class and as the full suite — killed it, so it was an invalid
  execution outcome under multi-suite load, not a defect at that coordinate. Recorded here
  because the plugin refuses any report containing one, so a repeat must be investigated
  rather than re-run away.

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
- `# async-routing` (adapter: `PagerDutyServiceVal.resolveEvent:114`,
  `triggerEvent:194`, `changeEvent:254`, ×2 each) — `retryDelay > 0` routes between
  `exceptionallyComposeAsync(delayedExecutor(retryDelay, timeUnit))` and
  `exceptionallyCompose`. At delay 0 both produce identical results and differ only in
  which thread runs the continuation; a positive delay is distinguishable only by
  wall-clock timing, which the determinism rules prohibit asserting. Both directions are
  exercised (the retry tests use a positive step delay, the logging tests use 0), so the
  arms are covered — only the *choice between them* is unobservable. Escape hatch: a clock
  seam replacing `delayedExecutor` would make the routing assertable. Mirrors the same
  acceptance on `incident-core`'s `IncidentServiceVal.retry`.
- `# copy-on-write` (adapter: `PagerDutyRequestExceptionBuilder.create:81`) — the
  `errors.size() > 1` boundary mutated to `>=`: at exactly one error the list is already
  the immutable `List.of(error)`, so wrapping it in `unmodifiableList` yields a
  content-equal and equally immutable list. The mutable-escape direction (`ORDER_ELSE`,
  multi-entry as-is) is killed by the error-list immutability assertions. Mirrors the same
  acceptance on `incident-io`'s `IncidentIoRequestException$Parser.create`.
- `# always-true-delegate` (config: `PagerDutyConfig$Parser.test:99`) —
  `return super.test(...)` where the superclass either returns true or
  throws on unknown fields; the constant-true mutant preserves the call and
  its side effects. Mirrors the same acceptance in incident-io.

Shrinking a baseline is always an improvement; growing one requires a reason
here.

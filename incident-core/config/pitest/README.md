# Mutation-testing baseline & triage policy

Each `pitest<Suite>` run is finalized by `pitest<Suite>Verify`, which diffs the
run's unkilled mutants (`SURVIVED` and `NO_COVERAGE`) against the accepted
baseline in `<suite>-accepted.csv` and **fails on anything new**. Baseline row
format: `class,method,line,mutator,status`. Full policy — the three legal
outcomes for a new survivor, determinism requirements, targeting rules —
lives in sava-build's `HARDENING.md`.

Never refresh with `-PupdateMutationBaseline` just to make the build pass:
kill the mutant, refactor it out of existence, or record its equivalence
reason below. Pure line drift (every new row a same-status shift of a
stale one, populations unchanged) passes on its own with a notice —
refresh at a convenient moment. Anything else fails with a per-row
classification (`shifted` vs `newly covered` vs unexplained) and a churn
tally: a newly covered row is triage, not churn, and identical rows are
sibling mutants of one compound condition — the comparison is a
multiset, so never hand-dedupe the CSV.

A baseline row may carry a trailing `# note` — `# untriaged` is the
conventional label for seeded debt. Notes are preserved across
`-PupdateMutationBaseline` / `-PunionMutationBaseline` rewrites, and the
verify task counts rows marked `# untriaged` so the debt stays a printed
number, not prose.

## Untriaged debt

A first baseline seeded from the pre-existing survivor population is triage
debt made explicit, not acceptance. List it here until each key is killed,
refactored away, or moved below with a reason.

- None. The 2026-07-24 initial seed (`config` 3, `api` 26, `json` clean) was
  triaged 2026-07-24: 21 killed with new tests (retry-delay arithmetic,
  give-up-after call counts, checked-exception propagation, transport-failure
  retries, blank/absent config values, and JUL-captured failure logging via
  the generated `JulRecorder`), 8 accepted below.

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
- `# async-routing` (api: `IncidentServiceVal.retry:71` ×3) — `retryDelay >
  0` routes between `exceptionallyComposeAsync(delayedExecutor(...))` and
  `exceptionallyCompose`: at delay 0 both produce identical results and
  differ only in which thread runs the continuation; a positive delay is
  distinguishable only by wall-clock timing, which the determinism rules
  prohibit asserting. Escape hatch: a clock seam injected into the service
  (replacing `delayedExecutor`) would make the routing assertable.

Shrinking a baseline is always an improvement; growing one requires a reason
here.

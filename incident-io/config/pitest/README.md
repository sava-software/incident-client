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

- None. The 2026-07-24 initial seed (`request` 6, `response` 1, `config` 4,
  `adapter` 19) was triaged 2026-07-24: 26 killed with new tests
  (comma-placement and empty-collection exact-body tests, values-array parser
  probes, sparse-config builder preservation, blank-alert-key UUID
  generation, exception accessors/canBeRetried boundaries and parse-failure
  messages) or removed by refactor (dead null-guards in
  `CreateIncidentRequestRecord.body` — the builder normalizes null
  collections to empty; dead `bearerToken` guard in
  `IncidentIoConfig.createClientBuilder` — the parser validates it present),
  4 accepted below. `request` and `response` are clean.
- **`adapter`, 7 rows, seeded 2026-08-06 by `mutationOwnershipAudit` adoption.**
  `IncidentIoClientImpl` and `IncidentIoClient$Builder` had no mutation-suite owner until
  the `adapter` targets were widened to cover the whole module; the rows are the
  pre-existing hole made visible, not new debt. All 7 are `NO_COVERAGE`: the response
  status gate (`lambda$static$0:26` ×4 and its parse return `:30`),
  `createIncident:50`, and the bearer-token request decorator
  (`Builder.lambda$bearerToken$0:44`). Closing them needs a wire-level test — an
  in-process `jdk.httpserver` harness that serves 2xx/non-2xx/unparseable bodies and
  observes the outgoing `Authorization` header — which `incident-pagerduty` and
  `incident-webhook` already have and this module does not (its `testModuleInfo` does not
  yet require `jdk.httpserver`). None of these is an equivalence claim.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# copy-on-write` (adapter: `IncidentIoRequestException$Parser.create:102`
  ×3) — `errors.size() > 1 ? unmodifiableList : errors` routing: both
  branches return a list with identical content (size ≤ 1 is already an
  immutable `List.of`), so the boundary/order mutants only change which
  content-equal instance escapes. Equal but not identical; killable only by
  asserting mutability the API does not promise.
- `# always-true-delegate` (config: `IncidentIoConfig$Parser.test:85`) —
  `return super.test(...)` where the superclass either returns true or
  throws on unknown fields; the mutated constant-true return preserves the
  call and its side effects, so no input can distinguish it. Escape hatch:
  a superclass path that returns false would make the propagation
  observable.

Shrinking a baseline is always an improvement; growing one requires a reason
here.

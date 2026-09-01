# Mutation-testing baseline & triage policy

`<suite>-accepted.csv` records this module's argued-unkilled mutants. The CSV's
`# line N` tag and the current PIT report are the only places a source coordinate
belongs — this README identifies a mutant by class, method, mutator, and the branch
being argued, never by line. `<suite>-pitest-version` and
`<suite>-pitest-toolchain.tsv` are committed beside them.

The legal outcomes for a new unkilled mutant, the determinism rules, and the named
writer tasks that may touch these files are all owned by sava-build, not by this
file: run `./gradlew :incident-io:hardeningHelp` and
`./gradlew :incident-io:hardeningAgentTemplate` for the installed version's
authority, and read sava-build's `HARDENING.md` for the doctrine behind them. Never
widen a baseline just to make a build pass.

Everything below the triaged heading is
grouped by the principle that makes it equivalent.

## Untriaged debt

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
- None for `adapter`. `:incident-io:mutationOwnershipAudit` adoption on 2026-08-06
  widened the `adapter` suite to cover the whole module and exposed 7 `NO_COVERAGE`
  rows — a pre-existing hole, not new debt: the response status gate, its
  parse-failure path, `createIncident`'s return, and the bearer-token request
  decorator, none of which any accessor reads back. All were closed 2026-08-07 by
  `IncidentIoClientWireTests`, an in-process `jdk.httpserver` harness (the module's
  `testModuleInfo` gained `jdk.httpserver` to host it) serving 200, 300, 400, 503 and
  a truncated 2xx body, and observing the outgoing `Authorization` header. One row
  remains, accepted below.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# copy-on-write` (adapter: `IncidentIoRequestException$Parser.create` ×3) —
  the `errors.size() > 1 ? unmodifiableList : errors` routing: both
  branches return a list with identical content (size ≤ 1 is already an
  immutable `List.of`), so the boundary/order mutants only change which
  content-equal instance escapes. Equal but not identical; killable only by
  asserting mutability the API does not promise.
- `# leading-comma-guard` (request: `CreateIncidentRequestRecord.body` and
  `CreateIncidentRequestRecord.appendArray`, both `ConditionalsBoundaryMutator`) — both
  are `sb.length() > 1` guards deciding whether a separating comma is needed, mutated to
  `>= 1`. `idempotency_key` is required and emitted first, and `build()` guarantees it is
  non-blank, so `appendField` always writes it: by the time any later section runs,
  `sb.length()` is at least the length of that pair and never 1, which makes `>` and `>=`
  agree. The one site where the boundary *is* observable — `appendField`'s own guard, on
  that very first field — is killed. Escape hatch: if `idempotency_key` ever became
  optional again, or a collection section were reordered ahead of it, these become
  reachable and this argument must be re-read. The guards stay because they are what keeps
  that reordering safe.
- `# unreachable-1xx` (adapter: `IncidentIoClientImpl.lambda$static$0`,
  `RemoveConditionalMutator_ORDER_IF` on the `statusCode < 200` arm of the response
  gate) — the mutant is observable only with a *final* HTTP status below 200, and
  `java.net.http.HttpClient` never surfaces one: 1xx interim responses are consumed
  inside the protocol layer, and `jdk.httpserver` refuses to emit them as a final
  status. Unreachable in-harness; a raw-socket harness writing a literal sub-200 status
  line would hang until the request timeout, and a flaky harness is worse than recorded
  debt. The `>= 300` arm is pinned by the 300/400/503 wire tests. Mirrors the same
  acceptance in `incident-webhook` and `incident-pagerduty`.
- `# always-true-delegate` (config: `IncidentIoConfig$Parser.test`,
  `BooleanTrueReturnValsMutator`) — `return super.test(...)` where the superclass
  either returns true or throws on unknown fields; the mutated constant-true return
  preserves the call and its side effects, so no input can distinguish it. Escape
  hatch: a superclass path that returns false would make the propagation observable.

Growing a baseline requires a reason here.

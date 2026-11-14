# Mutation-testing baseline & triage policy

`<suite>-accepted.csv` records this module's argued-unkilled mutants. The CSV's
`# line N` tag and the current PIT report are the only places a source coordinate
belongs — this README identifies a mutant by class, method, mutator, and the branch
being argued, never by line. `<suite>-pitest-version` and
`<suite>-pitest-toolchain.tsv` are committed beside them.

The legal outcomes for a new unkilled mutant, the determinism rules, and the named writer
tasks that may touch these files are all owned by sava-build. This repo runs
`:incident-pagerduty:hardeningHelp` and `:incident-pagerduty:hardeningAgentTemplate` for
the installed version's authority before touching a baseline, and reads sava-build's
`HARDENING.md` for the doctrine behind them. Never widen a baseline just to
make a build pass.

Everything below the triaged heading is
grouped by the principle that makes it equivalent.

## Untriaged debt

- None. The 2026-07-24 initial seed (`payload` 47, `config` 4, `adapter` 4,
  `response` clean) was triaged 2026-07-24: 30 killed with new tests
  (single-entry prototype-copy immutability, chained link/image/custom-detail
  adds, null-collection prototypes, typed custom-detail overloads, timestamp
  and dedup-key defaults, blank-vs-null branches of every optional field,
  sparse-config builder preservation, adapter delegation) or removed by
  refactor (dead null-guard in `customDetailsObject` — both constructors
  initialize the map), 20 accepted below. `response` and `adapter` are clean.
- None for `adapter`. `:incident-pagerduty:mutationOwnershipAudit` adoption on 2026-08-06
  exposed 130 rows: the suite had named three classes, leaving 22 of this module's
  production classes with no owner at all. Widening it to `event.client.*`,
  `event.service.*` and `exceptions.*` made a pre-existing hole visible, and all of it was
  worked down 2026-08-07 (221 mutants, 90 → 213 detected). What closed it:
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
    branch that chooses *which* request decorator gets installed: the default one, which
    sets `Content-Type` and no `Authorization` header (the `authToken` option that used
    to add one is gone), and a caller-supplied `extendRequest` that must be left alone.

  One observation worth keeping: the first widened multi-suite run reported a `RUN_ERROR`
  at `PagerDutyEventClient.asIncidentClient` (`NullReturnValsMutator`) — the default
  method that wraps the event client as an `IncidentClient`. A quiet history-free re-run —
  both scoped to that class and as the full suite — killed it, so it was an invalid
  execution outcome under multi-suite load, not a defect in that method. Recorded here
  because this repo investigates a repeat rather than re-running it away; consult
  `:incident-pagerduty:hardeningHelp` for how the installed plugin treats a report
  carrying one.

  Load is not the only thing that produces such an outcome. PIT 1.30.2's control channel
  is open to the port capture described under the reviewed items below: its main process
  binds port 0 on the wildcard address (`SocketFinder`), and each minion connects to
  `localhost` and gives up after a read timeout of 10 seconds (coverage) or 20 (mutation).
  Reduced to those two calls and run against one held loopback port, the minion's read
  timed out and the main process never saw a connection. On 2026-11-14 the `response`
  suite's coverage minion died with exactly that read timeout, ten seconds after it
  started, at a load average under 14; the next history-free gate was clean. Which port
  that run had was not recorded, so this too is a fit and not a capture. Nothing in this
  repository can change PIT's sockets.
- **Reviewed 2026-11-14, `adapter`: one `TIMED_OUT`, cause unknown, a demonstrated risk
  removed.** On 2026-11-12 a single observation reported `TIMED_OUT` for the `clientUrl`
  ternary of the trigger body, then in `PagerDutyEventClientImpl.triggerEvent`
  (`RemoveConditionalMutator_EQUAL_ELSE`), with the load average near 70 on 10 cores.
  - Mechanism, demonstrated 2026-11-13 on this machine (macOS, with
    `net.inet.tcp.randomize_ports` at 0, so ephemeral ports come in sequence): the wire
    harness `ClientTests` bound its server to the wildcard address, the only test server
    in this repository that did, and its client connected to `127.0.0.1`. macOS can give
    a wildcard bind of port 0 a port that another process's loopback listener already
    holds, and a connection to `127.0.0.1` then reaches that listener instead of the
    harness. With one loopback listener holding the port the harness was about to be
    given, `EventClientTests` failed on the client's 8-second request timeout when the
    listener stayed silent, and on a connection reset in a fraction of a second when the
    listener closed the connection. The first outlasts PIT's watchdog for that test at
    every load observed here: PIT allows 1.25 times the test's coverage-phase time plus 4
    seconds, and that test never took more than a few hundred milliseconds. Producing the
    stall needs no machine load.
  - The cause of the 2026-11-12 observation is unknown, and stays so: nothing kept from
    that run shows which port its server had or what its test saw, and that run named no
    covering test. The mechanism is consistent with it: the mutated code is a
    straight-line expression with no loop, wait or lock, and the test that killed that
    mutant in every report that names one ran through the harness. So is load: on
    2026-11-13 the unchanged harness ran through two gates and two solo runs at load
    averages between 6 and 27 with no `TIMED_OUT`, though by then `TriggerJsonTests`, not
    the harness, killed that mutant. Neither is excluded.
  - How often a capture could happen, as an illustration and not as the chance that one
    caused this observation: 29 loopback listeners were counted in the ephemeral range on
    2026-11-13, and the harness ran 16 times in one adapter-suite run, 14 of them in the
    mutation phase. If each of those binds were equally likely to be given any of the
    16,384 ports, independently of the others, a capture would occur in about one such
    run in 40. That assumes the counts of 2026-11-13, taken after the body's rendering
    moved to `triggerJson`, held on 2026-11-12, and says nothing of whether the capturing
    listener stays silent; none of that was measured.
  - Removed: the harness binds the loopback address, as every other wire test here does,
    and builds its client's endpoint from the bound address. `ClientTestRunner` asserts
    the loopback bind, that the harness's client targets the bound address, and the
    endpoint's literal form. A second loopback bind of a held port is refused, and a
    loopback listener takes `127.0.0.1` connections ahead of a wildcard one. Against the
    same held port the harness is now given another port, and the test passes.
  - Also done: the body's rendering moved to `PagerDutyEventClientImpl.triggerJson`, which
    `TriggerJsonTests` pins without a socket.
  - Not covered by either change: a socket test slowed past the watchdog by load alone.
    That has not been demonstrated here, and the 2026-11-12 observation does not exclude
    it. Every wire test in this module still opens a loopback socket, so a `TIMED_OUT`
    at a mutant one of them covers is a new finding: classify it from that run's report
    and its Gradle daemon log (`:incident-pagerduty:pitestAdapterDiagnostic` gives
    verbose output, which is diagnosis and not mutation evidence). A later kill does not
    close it.
  - No `adapter-timeouts.csv` row exists: the mutant is finite, and sava-build's
    HARDENING.md has such a mutant kept "out of the audited set while its contract-first
    repair or acceptance is completed" instead of a `cause:untriaged` draft row being
    committed.
  - Review, 2026-11-14: the harness change is accepted as removing the demonstrated
    risk, checked by the tests above and by a history-free module gate on the changed
    harness, which showed no `TIMED_OUT` and no unmutated failure at a load average near
    56. The cause of the observation is not thereby decided.
- **Reviewed 2026-11-14, `adapter`: one unmutated failure of the wire harness, cause
  unknown.** In the
  invocation that followed that observation, with the load average near 65, PIT refused
  to run because `ClientTestRunner`'s first dynamic test (`EventClientTests`) failed
  without mutation, about two seconds after the minion received its tests. PIT's verbose
  logging was off, and the Gradle daemon logs of both runs hold no cause.
  - The same mechanism can produce it: a captured connection that the other listener
    closes fails that test at once, where a request timeout would have taken eight
    seconds.
  - Under the same illustrative assumptions, a capture at the one bind this failure
    involves, the first of the coverage phase, would occur about once in 565 such binds.
    That is a frequency under those assumptions, not the chance that a capture caused
    this failure, which cannot be judged without the port or the error from that run.
    Neither was kept.
  - Not excluded: the client and that test had both changed earlier the same day
    (`e4001e9`, the builder hand-off, and `2bcddcd`, the body-size check, on the request
    path; `f6e3812`, the 2xx response parser; `baa8e9a`, the removed `authToken`, in the
    test). The test passed three times outside PIT straight afterwards and has passed in
    every run since.
  - The cause is unknown, and stays so. The harness change removes the one mechanism
    that was demonstrated, and the review of 2026-11-14 accepted it on that footing; a
    repeat is a new finding, diagnosed from that run's daemon log and the diagnostic task
    above, not re-run away.
  - A wrong-reason failure of this kind during the mutation phase would have been
    recorded as a kill. A history-free gate on the changed harness detects the same
    mutants as the run before the change, so none of the current kills depends on a
    capture; whether an earlier run recorded one cannot be told from its statuses.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# copy-on-write` (payload: the `size() == 1` re-copy branch of
  `customDetailsObject`, `link` and `image`) — forcing the re-copy branch
  also copies larger mutable maps/lists, preserving their content and subsequent
  builder updates. Skipping the copy at size one instead attempts to mutate
  `Map.of`/`List.of` and is killed by the chained-add tests. Both builders'
  `create` methods take immutable snapshots independently of these setter branches;
  snapshot and accessor-immutability tests cover empty, single-entry and larger
  collections, including a singleton map produced by replacing the same key.
- `# truncation-boundary` (payload: `summary`, the truncation guard) — `length() > 1_024`
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
- `# async-routing` (adapter: `PagerDutyServiceVal.resolveEvent`, `triggerEvent`,
  `changeEvent`, ×2 each) — the `retryDelay > 0` guard routes between
  `exceptionallyComposeAsync(delayedExecutor(retryDelay, timeUnit))` and
  `exceptionallyCompose`. At delay 0 both produce identical results and differ only in
  which thread runs the continuation; a positive delay is distinguishable only by
  wall-clock timing, which the determinism rules prohibit asserting. Both directions are
  exercised (the retry tests use a positive step delay, the logging tests use 0), so the
  arms are covered — only the *choice between them* is unobservable. Escape hatch: a clock
  seam replacing `delayedExecutor` would make the routing assertable. `incident-core`
  took that hatch for `IncidentServiceVal` and retired the same acceptance there on
  2026-11-12; `PagerDutyServiceVal` has no such seam, so the argument stands here.
- `# copy-on-write` (adapter: `PagerDutyRequestExceptionBuilder.create`) — the
  `errors.size() > 1` boundary mutated to `>=`: at exactly one error the list is already
  the immutable `List.of(error)`, so wrapping it in `unmodifiableList` yields a
  content-equal and equally immutable list. The mutable-escape direction (`ORDER_ELSE`,
  multi-entry as-is) is killed by the error-list immutability assertions. Mirrors the same
  acceptance on `incident-io`'s `IncidentIoRequestException$Parser.create`.
- `# always-true-delegate` (config: `PagerDutyConfig$Parser.test`) —
  `return super.test(...)` where the superclass either returns true or
  throws on unknown fields; the constant-true mutant preserves the call and
  its side effects. Mirrors the same acceptance in incident-io.

Growing a baseline requires a reason here.

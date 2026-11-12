# Mutation-testing baseline & triage policy

`<suite>-accepted.csv` records this module's argued-unkilled mutants. The CSV's
`# line N` tag and the current PIT report are the only places a source coordinate
belongs — this README identifies a mutant by class, method, mutator, and the branch
being argued, never by line. `<suite>-pitest-version` and
`<suite>-pitest-toolchain.tsv` are committed beside them.

The legal outcomes for a new unkilled mutant, the determinism rules, and the named
writer tasks that may touch these files are all owned by sava-build, not by this
file: run `./gradlew :incident-webhook:hardeningHelp` and
`./gradlew :incident-webhook:hardeningAgentTemplate` for the installed version's
authority, and read sava-build's `HARDENING.md` for the doctrine behind them. Never
widen a baseline just to make a build pass.

Everything below the triaged heading is
grouped by the principle that makes it equivalent.

## Untriaged debt

- None. The module was hardened at creation (2026-07-26): the initial `format`
  survivors (2) were killed with a null-`customDetails` custom-alert test, and
  the initial `config` survivors were killed with wire-level wiring tests
  (headers/bearer token observable only on requests) or removed by refactor
  (the `headers.size() > 1` copy-on-write conditional — header order is
  contractual, so every size takes the order-preserving copy; the `Map.copyOf`
  branch's kill was hash-salt-dependent, i.e. flaky, and the conditional was
  only an allocation micro-optimization). The initial `adapter` survivors were
  killed with a headers-plus-`extendRequest` composition wire test, a
  300-status boundary test, toString content assertions, and exception
  accessor/null-body tests, or removed by refactor (the `body.length == 0`
  arms in the empty-body reads — `new String(byte[0])` is already `""`, so the
  length check was a content-equal branch); 3 accepted below (plus 1 in
  `config`). The Telegram expansion (2026-07-26) added one accepted `format`
  row (truncation boundary, retired 2026-11-12: see `# boundary-identity`
  below) and killed its factory's blank-chat-id direction by pinning the
  factory's error message against the record's own validation.

## Triaged equivalent mutants (accepted with reasons)

Group by the principle that makes them equivalent (see the recurring families
in HARDENING.md); the baseline CSVs carry the exact keys.

- `# boundary-identity` — retired 2026-11-12; no row carries it, and the
  `format` baseline is empty. It argued the `>=` mutant of
  `TelegramTextFormat.render`'s `text.length() > MAX_TEXT_LENGTH` guard: at
  exactly the limit `substring(0, MAX_TEXT_LENGTH)` returned the same string,
  so no input could tell the branches apart. The truncation branch now moves
  the cut back from a high surrogate, so text of exactly the limit that ends
  in one is sent whole by `>` and cut by `>=`;
  `TelegramTextFormatTests#textOfExactlyTheLimitIsSentAsItIsWhateverItsLastChar`
  kills it. The row was dropped with `pitestFormatBaselinePrune` after two
  matching fresh history-free previews and the write-boundary run. An unkilled
  mutant in `render` is new debt, not this family.
- `# always-true-delegate` (config: `WebhookConfig$Parser.test`,
  `BooleanTrueReturnValsMutator`) — the `return super.test(...)` delegation,
  where the superclass either returns true or throws on unknown fields; the
  mutated constant-true return preserves the call and its side effects, so no
  input can distinguish it. Escape hatch: a superclass path that returns false
  would make the propagation observable.
- `# empty-noop-guard` (adapter: `WebhookClient$Builder.createClient`,
  `RemoveConditionalMutator_EQUAL_IF`) — the `if (!headers.isEmpty())` guard
  around building the header-applying request wrapper; with no headers the
  wrapper iterates an empty map and returns the builder unchanged, so forcing
  the guard true only swaps a null `extendRequest` (identity in
  `JsonHttpClient`) for an observable no-op.
- `# unreachable-1xx` (adapter: `WebhookClientImpl.lambda$static$0`,
  `RemoveConditionalMutator_ORDER_IF`) — the `statusCode < 200` arm of the
  success check in the status predicate. Only a *final* 1xx status
  distinguishes this direction, and `java.net.http` never surfaces one:
  interim responses are consumed by the protocol layer, so a raw-socket
  harness would hang or flap. A flaky harness is worse than recorded debt;
  the `>= 300` arm is pinned by the 300/400/503 wire tests.
- `# defensive-null-guard` (adapter: `WebhookClientImpl.lambda$static$0`,
  `RemoveConditionalMutator_EQUAL_ELSE`) — the `body == null ? "" : new
  String(body, UTF_8)` ternary in the same static lambda: `readBody` over the
  `ofByteArray` body handler never yields null (an empty body yields an empty
  array), so the mutated always-decode arm can only NPE on an input the
  transport cannot produce. The guard stays for the `readBody` contract,
  which declares null a legal return.

Growing a baseline requires a reason here.

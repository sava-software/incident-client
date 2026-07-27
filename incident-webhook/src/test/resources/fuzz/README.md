# Fuzz seed corpora

Seed provenance lives here, next to — never inside — the corpus directories:
a file inside a corpus dir would itself be fed to the harness as a seed.

## `format/` (regression corpus for `WebhookFormatFuzz`)

- `full-fields.bin` — hand-written full-coverage input: a severity selector byte
  and all five NUL-delimited string fields plus a custom-detail entry, with `"`,
  `\`, a raw newline, and the Slack entity characters `&`/`<`/`>` in values to
  pin both the JSON escaping and the entity transform. The NUL-delimited format
  is trivial for the mutator to reach from scratch, so this corpus exists as the
  landing place for findings (each committed as a seed plus a named regression
  test), not to buy coverage.

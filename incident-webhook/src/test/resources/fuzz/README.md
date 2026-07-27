# Fuzz seed corpora

Seed provenance lives here, next to — never inside — the corpus directories:
a file inside a corpus dir would itself be fed to the harness as a seed.

## `format/` (regression corpus for `WebhookFormatFuzz`)

- `full-fields.bin` — hand-written full-coverage input: a severity selector byte
  and all NUL-delimited string fields (summary, key, details, source, a
  custom-detail entry, and the Telegram chat id), with `"`, `\`, a raw newline,
  and the Slack entity characters `&`/`<`/`>` in values to pin the JSON
  escaping, the entity transform, and the Slack/Telegram differential. The NUL-delimited format
  is trivial for the mutator to reach from scratch, so this corpus exists as the
  landing place for findings (each committed as a seed plus a named regression
  test), not to buy coverage.

# Fuzz seed corpora

Seed provenance lives here, next to — never inside — the corpus directories:
a file inside a corpus dir would itself be fed to the harness as a seed.

## `payload/` (regression corpus for `PagerDutyPayloadFuzz`)

- `full-fields.bin` — hand-written full-coverage input: leading severity-selector
  byte, non-blank summary containing a raw line feed (pins the documented
  LF/CR-stripping transform), source, component, group, event class, a
  custom-details entry, and link/image references, with `"` and `\` in string
  values to pin JSON escaping. The NUL-delimited format is trivial for the
  mutator to reach from scratch, so this corpus exists as the landing place for
  findings (each committed as a seed plus a named regression test), not to buy
  coverage.

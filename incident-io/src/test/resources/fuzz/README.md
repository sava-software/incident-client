# Fuzz seed corpora

Seed provenance lives here, next to — never inside — the corpus directories:
a file inside a corpus dir would itself be fed to the harness as a seed.

## `request/` (regression corpus for `CreateIncidentRequestFuzz`)

- `full-fields.bin` — hand-written full-coverage input: all nine scalar fields, a
  role assignment with a populated assignee reference, and a custom-field entry,
  with `"` and `\` in string values to pin JSON escaping. The NUL-delimited format
  is trivial for the mutator to reach from scratch, so this corpus exists as the
  landing place for findings (each committed as a seed plus a named regression
  test), not to buy coverage.

## `response/` (bootstrap corpus for `CreateIncidentResponseFuzz`)

- `full.json` — a complete `IncidentV2` response body: nested creator/assignee
  user references, custom-field entries, duration metrics, and timestamps. The
  nested object/array structure would take a from-scratch mutator a long time to
  assemble, which is what makes this corpus worth committing beyond regression
  duty.

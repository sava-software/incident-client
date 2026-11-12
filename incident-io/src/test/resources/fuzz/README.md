# Fuzz seed corpora

Seed provenance lives here, next to — never inside — the corpus directories:
a file inside a corpus dir would itself be fed to the harness as a seed.

## `request/` (regression corpus for `CreateIncidentRequestFuzz`)

- `full-fields.bin` — hand-written full-coverage input: all ten scalar fields, a
  role assignment with a populated assignee reference, a custom-field entry with
  every typed value set, an incident timestamp value, and retrospective incident
  options, with `"` and `\` in string values to pin JSON escaping. The
  NUL-delimited format is trivial for the mutator to reach from scratch, so this
  corpus exists as the landing place for findings (each committed as a seed plus
  a named regression test), not to buy coverage.
- `blank-required-ids.bin` — a `fuzzRequest` finding from 2026-07-27, minimized
  by libFuzzer: every field but the name empty. It caught `custom_field_id`
  being dropped when blank while its sibling required id `incident_role_id` was
  always emitted, so the body silently lost the entry the caller asked for.
  Regression test: `CreateIncidentRequestTests#blankRequiredIdsAreSerializedRatherThanDropped`.

## `response/` (bootstrap corpus for `CreateIncidentResponseFuzz`)

The response target exercises both success and error parsers independently on each
input, so a rejection by one parser does not prevent coverage of the other.

- `full.json` — a complete `IncidentV2` response body: all four `ActorV2` creator
  variants, a custom-field entry carrying every `CustomFieldValueV2` shape
  (option, catalog entry with aliases, link, numeric, text), a `UserV2` role
  assignee, duration metrics, timestamps, postmortem document ids, and team ids.
  The nested object/array structure would take a from-scratch mutator a long time
  to assemble, which is what makes this corpus worth committing beyond regression
  duty. Extended 2026-08-28 with the `IncidentV2` fields incident.io added that
  day — `last_activity_at`, `team_ids`, `ms_teams_channel_url` and
  `slack_channel_url` — and, in the same pass, with the previously narrowed
  members of the nested records (`CustomFieldTypeInfoV2` description/options,
  and the timestamps, ranks and flags on the status, severity, type, role and
  incident-timestamp records), so the bootstrap body is now the full upstream
  shape rather than the subset this client used to model.
  Extended 2026-10-16 against incident.io Go SDK schema commit `91f1a9a` with
  `IncidentV2.debriefs`: two `IncidentDebriefV2` entries carrying required ids,
  start/end timestamps, distinct offsets, and fractional-second precision.
- `error.json` — hand-written `ErrorResponse` bootstrap input added 2026-10-16
  against incident.io Go SDK schema commit `91f1a9a`. Covers per-error metadata
  with escaped keys and values, source locations, rate limits and retry timestamps,
  and debug stack frames. Structured-error unit tests assert preservation of the
  same diagnostic fields using separate inline examples; corpus replay exercises
  the parser on this shape during `check`.

## `alertEvent/` (regression corpus for `AlertEventFuzz`)

Added 2026-11-12 against incident.io Go SDK schema commit `483d54e`
(`AlertEventsCreateHTTPPayloadV2`). The leading byte picks the status (even: `firing`,
odd: `resolved`); the rest is NUL-delimited title, deduplication key, description,
source URL, then metadata key/value pairs. The format is trivial for the mutator to
reach from scratch, so this corpus exists as the landing place for findings (each
committed as a seed plus a named regression test), not to buy coverage.

- `full-fields.bin` — hand-written: a firing event with every member set; `"`, `\`
  and BMP non-ASCII characters in the title and key; a markdown description with a
  newline, a tab, CR/LF and a raw control character (all escaped, never removed); a
  source URL with a quoted query; and metadata with an escaped value, a key holding a
  newline whose value holds the input's one astral character, an empty-string key, and a
  repeated key that replaces the first in place.
- `resolved-blank-optionals.bin` — hand-written: a resolved event whose description is
  whitespace and whose source URL is empty, so both are omitted and no metadata object
  is written.

## `alertEventResponse/` (regression corpus for `AlertEventResponseFuzz`)

Added 2026-11-12 against incident.io Go SDK schema commit `483d54e`
(`AlertEventsCreateHTTPResultV2`). A flat object of three strings is within easy reach
of the mutator, so this corpus too is a regression landing place rather than a
bootstrap.

- `example-202.json` — the schema's own 202 example, its members in the same order,
  compacted to one line.
- `unknown-member.json` — hand-written: the three members out of schema order around an
  unknown member holding a nested object and array, with escapes in the string values.

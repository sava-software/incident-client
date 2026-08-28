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

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

- `nonfinite-nan.bin`, `nonfinite-positive-infinity.bin`, and
  `nonfinite-negative-infinity.bin` — handcrafted regression inputs selecting
  NaN and both infinities for both `Double` and `Float` custom details. Both
  event builders must reject these at `create()` with `IllegalArgumentException`;
  the same input still exercises the existing string round-trip oracle.
- `raw-double-nan.bin` and `raw-float-nan.bin` — handcrafted IEEE 754 NaN bit
  patterns, `7ff8000000000001` and `7fc00001`, respectively, pin the raw-bit
  rejection path independently of the special-value selector. These are
  regression seeds, not outputs from a fuzz campaign.

The leading byte retains its severity selection (modulo the severity count);
its upper six bits additionally select floating-point cases: 1 is NaN, 2 is
positive infinity, 3 is negative infinity, and 4 is negative zero. Every other
selector interprets up to the next eight bytes as big-endian raw `Double` bits
and the low 32 bits as raw `Float` bits. Those bytes remain part of the original
NUL-delimited string input. Numeric checks run independently before the string
oracle, including when the string summary is blank. Finite values must round-trip
at their original precision, preserving signed zero; nonfinite values must be
rejected by both event and change-event builders through the `Number` overload.

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
  test), not to buy coverage. It has no topic part, so it pins the topic-less body.
- `topic.bin` — `full-fields.bin` plus an eighth NUL-delimited part, the Telegram topic
  `forum-topic`. The harness takes that part's `String.hashCode()` with the sign bit
  cleared as the message thread id (an absent or empty part is 0, no topic), so this seed
  sends `message_thread_id` 415388035 and pins its round trip as a JSON number. Added as
  the landing place for the topic field, not measured against a mutation score.

## `retryAfter/` (regression corpus for `WebhookRetryAfterFuzz`)

The harness reads each input twice: whole, decoded as UTF-8 and handed to
`TelegramTextFormat.botApiRetryAfter` and the format's `retryAfter` hook (oracle 1, garbage
in), and split, as a big-endian `long` in its first eight bytes followed by a UTF-8
description that it wraps in the Bot API's 429 body (oracle 2, round trip).

- `canonical-429.json` — the Bot API's 429 error body as its server writes it,
  `{"ok":false,"error_code":429,"description":"Too Many Requests: retry after 35","parameters":{"retry_after":35}}`,
  taken from the Bot API page's `ResponseParameters` and `tdlib/telegram-bot-api`'s
  `Query.cpp`. Oracle 1 reads it as a 35-second wait; oracle 2 reads its first eight
  bytes, `{"ok":fa`, as `n` = 8872776721799734881 and the rest as the description.
- `retry-after-35.bin` — the oracle-2 layout written out: `n` = 35 as eight big-endian
  bytes (`00 00 00 00 00 00 00 23`), then `Too Many Requests: retry after 35`. Oracle 1
  reads it as no wait.

Both exist as the landing place for findings (each committed as a seed plus a named
regression test), not to buy coverage, and have not been measured against a mutation
score.

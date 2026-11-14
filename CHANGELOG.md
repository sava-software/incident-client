# Changelog

## [25.6.0](https://github.com/sava-software/incident-client/compare/25.5.3...25.6.0) (2026-11-14)


### ⚠ BREAKING CHANGES

* **incident-webhook:** the `TelegramTextFormat` record gains a second component, `messageThreadId`. A record pattern that deconstructs it with one component no longer compiles. The one-argument constructor, `chatId()` and code already compiled against the old shape are unaffected. A Properties config whose `<prefix>messageThreadId` key was ignored before is now validated for every provider and for a composing factory: a value that is not blank and not ASCII digits from 0 to 2147483647 fails the parse.
* **incident-pagerduty:** `PagerDutyEventClient.Builder.authToken`, `PagerDutyConfig.authToken()` and the `authToken` config key are removed. A JSON config that still carries `authToken` fails to parse with "Unknown HttpApiClientConfig field authToken"; the Properties form ignores the key.

### Features

* **incident-core:** read the HTTP-date form of Retry-After ([7a660bb](https://github.com/sava-software/incident-client/commit/7a660bb164a3e4a3995ff6284c59a38e33270096))
* **incident-core:** wait out a provider's Retry-After before the next attempt ([a488500](https://github.com/sava-software/incident-client/commit/a48850001caed133151de8a52c2c4891e507c249))
* **incident-io:** add an alert-source client that can resolve what it reports ([a1d4d2c](https://github.com/sava-software/incident-client/commit/a1d4d2cd60fa6f82727d6783a718613611b66f33))
* **incident-webhook:** disable link previews on Telegram alerts ([1e9cc5a](https://github.com/sava-software/incident-client/commit/1e9cc5af35349bb8e5031029e1851f90798baa5b))
* **incident-webhook:** let a WebhookFormat read the wait from a failed response body ([3455ffe](https://github.com/sava-software/incident-client/commit/3455ffeb0769e4759f906179f1ec4e4e2091c16e))
* **incident-webhook:** read the Bot API's retry_after from a failed response body ([cfcb52c](https://github.com/sava-software/incident-client/commit/cfcb52ca5aa230aaf9ea2df9c1d9b83ed04b8591))
* **incident-webhook:** send Telegram alerts to a forum topic ([27229fe](https://github.com/sava-software/incident-client/commit/27229fe05356f353a8c0d3cc59ef5d5bec02c2e1))


### Bug Fixes

* **build:** upgrade to Gradle 9.9 and scope plugin repositories ([14c823f](https://github.com/sava-software/incident-client/commit/14c823f13c5e4be427afd7a07e0112e14d7d84ca))
* **deps:** bump solanaBOMVersion to 25.30.49 for sava-rpc's typed HTTP failures ([fa40c55](https://github.com/sava-software/incident-client/commit/fa40c55849841287ce4b8caf1842a45399199be0))
* **incident-core:** fail with the last attempt's error when retries run out ([080dcf3](https://github.com/sava-software/incident-client/commit/080dcf3193b805374435027174200db66a488bf6))
* **incident-core:** refuse an HTTP-date before the year 1900 ([eb175af](https://github.com/sava-software/incident-client/commit/eb175af01aa6fc0aab2a9310131b4119e0c4a190))
* **incident-pagerduty:** create builders handed to the client and refuse blank link and image URLs ([e4001e9](https://github.com/sava-software/incident-client/commit/e4001e976f7bd3429edd52b1ac43a430d1f4b635))
* **incident-pagerduty:** do not retry a 2xx response whose body cannot be parsed ([f6e3812](https://github.com/sava-software/incident-client/commit/f6e3812d481ef87036ce17b970e3b850dc3d2914))
* **incident-pagerduty:** fail with the last attempt's error when retries run out ([f9a124c](https://github.com/sava-software/incident-client/commit/f9a124c82962f421449fb58a3c3d91cefedfa7e0))
* **incident-pagerduty:** refuse a request body over the 512 KB the Events API accepts ([2bcddcd](https://github.com/sava-software/incident-client/commit/2bcddcd106a50b5d3f4057cfb39b219bb87c76bd))
* **incident-pagerduty:** stop sending a REST API token to the Events API ([baa8e9a](https://github.com/sava-software/incident-client/commit/baa8e9ad616ed8e7bfbbd66fcd7ac5a88518e5db))
* **incident-webhook:** keep a surrogate pair whole when Telegram text is truncated ([d1938ca](https://github.com/sava-software/incident-client/commit/d1938ca014ef5aa112e2f4e92e0e66018a47c3d3))

## [25.5.3](https://github.com/sava-software/incident-client/compare/25.5.2...25.5.3) (2026-10-29)


### Features

* **incident-core:** shorten alert keys to the client's declared limit ([0031a5b](https://github.com/sava-software/incident-client/commit/0031a5b62eed5837cf6507e4cfeeb2b528b8e230))

## [25.5.2](https://github.com/sava-software/incident-client/compare/25.5.1...25.5.2) (2026-10-26)


### Bug Fixes

* **incident-core:** fail unsupported service resolution once without retries ([3e1f4f4](https://github.com/sava-software/incident-client/commit/3e1f4f4deb8061f7ce0c4c3344304674172b21eb))
* **incident-core:** reuse alert keys across IncidentService retries ([3e1f4f4](https://github.com/sava-software/incident-client/commit/3e1f4f4deb8061f7ce0c4c3344304674172b21eb))
* **incident-io:** serialize visibility with Locale.ROOT ([f3f83b5](https://github.com/sava-software/incident-client/commit/f3f83b5d5e6a40f9a130fd38f1d86ab42345fd5c))
* **incident-webhook:** fail resolution of keyless service reports instead of returning null ([3e1f4f4](https://github.com/sava-software/incident-client/commit/3e1f4f4deb8061f7ce0c4c3344304674172b21eb))
* **incident-webhook:** include generated service keys in messages and responses ([3e1f4f4](https://github.com/sava-software/incident-client/commit/3e1f4f4deb8061f7ce0c4c3344304674172b21eb))

## [25.5.1](https://github.com/sava-software/incident-client/compare/25.5.0...25.5.1) (2026-10-17)


### Features

* **hardening:** update to sava-build 21.5.28 and sync hardening template ([e0de4c7](https://github.com/sava-software/incident-client/commit/e0de4c7f7ae7b59e30328e1ca7fcec77e3478b0d))
* **hardening:** upgrade PIT framework and toolchains to 1.30.0 ([f613850](https://github.com/sava-software/incident-client/commit/f6138508ab02b31ccc37eb30dd175124a72fac99))
* **incident-io:** enhance incident schema and serialization ([23e539f](https://github.com/sava-software/incident-client/commit/23e539f9151902add33081dc57aabc89bb2ef383))
* **incident-io:** expose debriefs and structured errors ([216b2b9](https://github.com/sava-software/incident-client/commit/216b2b932bd7961efbe5abfe9372ec1462ad2e2f))


### Bug Fixes

* **ci:** restrict workflow token permissions ([7837b5f](https://github.com/sava-software/incident-client/commit/7837b5f7ae7b1ebba43e7620bbcf0b7334e59cf7))
* **incident-io:** address metadata and module review findings ([e0251f7](https://github.com/sava-software/incident-client/commit/e0251f7141548254933f9f0377bc2e98246412d9))
* **incident-pagerduty:** snapshot payloads and reject nonfinite numbers ([bb4b4a2](https://github.com/sava-software/incident-client/commit/bb4b4a2bb9f2a8d31ec673976796d528ab457e2b))

## [25.5.0](https://github.com/sava-software/incident-client/compare/25.4.0...25.5.0) (2026-08-07)


### ⚠ BREAKING CHANGES

* **incident-io:** enforce the API's required fields and tolerate null optionals

### Features

* **hardening:** complete the baseline schema, provenance, and ownership migration ([60010d1](https://github.com/sava-software/incident-client/commit/60010d100e068fc7bc47b4aa48196f6ca753430b))
* **incident-io:** enforce the API's required fields and tolerate null optionals ([9cf8979](https://github.com/sava-software/incident-client/commit/9cf89797b59c92a91974cd433b3f4db5517c7e86))


### Bug Fixes

* **incident-io:** parse the new required duration-metric status field ([e65fe01](https://github.com/sava-software/incident-client/commit/e65fe015ef6e022889a1248031547d9d97fead63))


### Miscellaneous Chores

* release 25.5.0 ([de30d8d](https://github.com/sava-software/incident-client/commit/de30d8db224516dce0353e2f72936a0a484e3a09))

## [25.4.0](https://github.com/sava-software/incident-client/compare/25.3.0...25.4.0) (2026-07-28)


### Features

* **incident-io:** add support for incident timestamp IDs in alerts ([1c993a7](https://github.com/sava-software/incident-client/commit/1c993a776898bdd09b8257e6c619c034218437e5))
* **incident-io:** append source and custom details to incident summaries ([a565158](https://github.com/sava-software/incident-client/commit/a565158d932445d279c771e914342cb31e5b7af8))
* **incident-io:** support postmortem document IDs and extend parsing ([c8d1dcc](https://github.com/sava-software/incident-client/commit/c8d1dcc816782298680d33a0be12b5eb7835bea0))
* **incident-webhook:** add support for custom providers with tests ([00a96ee](https://github.com/sava-software/incident-client/commit/00a96eef9cd7617d092d3f5be9fc7dcc0d85920b))
* **incident-webhook:** add Telegram provider support ([774df64](https://github.com/sava-software/incident-client/commit/774df645ac83bf4d40772521fef09150122b4ce2))
* **incident-webhook:** add webhook client and factory with tests ([eb3ae81](https://github.com/sava-software/incident-client/commit/eb3ae81765d95e069b15c86368c75f4ec78975ca))


### Miscellaneous Chores

* release 25.4.0 ([9c685ba](https://github.com/sava-software/incident-client/commit/9c685ba334e5c6e16db3c53ddac778580675e3f6))

## [25.3.0](https://github.com/sava-software/incident-client/compare/25.2.0...25.3.0) (2026-07-26)


### Features

* **build:** log plugin resolution with local repo for debug visibility ([617ec6d](https://github.com/sava-software/incident-client/commit/617ec6d41b8f05a7ade559e2c6da7887aff5bd51))
* **fuzz:** add seed corpora and corpus README for fuzzing targets ([acab1b3](https://github.com/sava-software/incident-client/commit/acab1b33f8da54467b3b52d46b76e208247f3a2a))
* **incident-clients:** add explicit factory registration and improve test coverage ([61c84ba](https://github.com/sava-software/incident-client/commit/61c84ba74cdf1e2927097d051b3ab5f14fa35ac5))
* **incident-clients:** add provider-neutral IncidentClient factory and configs ([efd98a1](https://github.com/sava-software/incident-client/commit/efd98a13f989aa0854ae346629d099f6d856714d))
* **incident-io:** add README and improve examples for incident.io client ([9e27c72](https://github.com/sava-software/incident-client/commit/9e27c72c0120a50a532e6cd1e6c067b42f92c437))
* **json-parsing:** optimize parsers with FieldMatcher and improve parsing flow ([103d368](https://github.com/sava-software/incident-client/commit/103d36836e29cac4bfaa2a03339bbdfd03aaf3c3))


### Bug Fixes

* **json-util:** update escape behavior and ensure newline preservation ([0c778ee](https://github.com/sava-software/incident-client/commit/0c778ee5bde9e1de93e35679d83a8aa9246986cb))


### Miscellaneous Chores

* release 25.3.0 ([7a26921](https://github.com/sava-software/incident-client/commit/7a26921c071435bc257be70a56ec4e7a9270025a))

## [25.2.0](https://github.com/sava-software/incident-client/compare/25.1.2...25.2.0) (2026-07-24)


### Features

* **pagerduty:** add edge-case tests and enforce required fields ([f6a60f7](https://github.com/sava-software/incident-client/commit/f6a60f738a20c108c7e6214e83572d4506161352))


### Miscellaneous Chores

* release 25.2.0 ([1e47f9c](https://github.com/sava-software/incident-client/commit/1e47f9c8e2d0d394402875c3d907ebfa41830c4c))

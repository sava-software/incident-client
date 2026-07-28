# Changelog

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

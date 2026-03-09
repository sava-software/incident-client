# PagerDuty Event Client

This client aims to be compliant with the latest version of
the [PagerDuty Event API](https://developer.pagerduty.com/api-reference/f80f5db9acbe3-pager-duty-v2-events-api), currently V2. It also only aims to be
supported against the latest GA OpenJDK release.

## [Example Usage](incident-examples/src/main/java/software/sava/incident/examples/PagerdutyExamples.java)

```java
var client = PagerDutyEventClient.build()
    .defaultClientName("CLIENT_NAME")
    .defaultRoutingKey("INTEGRATION_KEY")
    .authToken("AUTH_TOKEN")
    .create();

final var bigInteger = new BigInteger("20988936657440586486151264256610222593863921");
final var payload = PagerDutyEventPayload.build()
    .summary("ex-summary")
    .source("ex-source")
    .severity(PagerDutySeverity.critical)
    .timestamp(ZonedDateTime.now(UTC))
    .component("ex-component")
    .group("ex-group")
    .eventClass("ex-class")
    .customDetails("ex-num-metric", 1)
    .customDetails("ex-boolean", true)
    .customDetails("ex-string", "val")
    .customDetails("ex-nested-json", """
        {"ex": "json"}"""
    )
    .customDetails("ex-big-decimal", new BigDecimal(bigInteger).add(BigDecimal.valueOf(0.123456789)))
    .customDetails("ex-big-integer", bigInteger)
    .link(PagerDutyLinkRef.build()
        .href("https://github.com/sava-software/incident-client")
        .text("Sava Incident PagerDuty Event Client")
        .create())
    .image(PagerDutyImageRef.build()
        .src("https://www.pagerduty.com/wp-content/uploads/2016/05/pagerduty-logo-green.png")
        .href("https://www.pagerduty.com/")
        .alt("pagerduty")
        .create())
    .create();

final var triggerResponseFuture = client.triggerDefaultRouteEvent(payload);

final var changeEventPayload = PagerDutyChangeEventPayload.build(payload).create();
final var changeEventResponseFuture = client.defaultRouteChangeEvent(changeEventPayload);

final var triggerResponse = triggerResponseFuture.join();

final var ackResponse = client.acknowledgeEvent(triggerResponse.dedupKey()).join();

final var resolveResponse = client.resolveEvent(triggerResponse.dedupKey()).join();

final var changeEventResponse = changeEventResponseFuture.join();
```

package software.sava.incident.examples;

import software.sava.incident.pagerduty.event.client.PagerDutyEventClient;
import software.sava.incident.pagerduty.event.data.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;

public final class PagerdutyExamples {

  static void main(final String[] args) {
    final int numArgs = args.length;
    if (numArgs == 0) {
      System.err.println("Usage: <routing_key> <auth_token>");
      System.exit(1);
      return;
    }

    final var routingKey = args[0];
    final var clientBuilder = PagerDutyEventClient.clientBuilder()
        .defaultClientName("sava-example")
        .defaultRoutingKey(routingKey);
    if (numArgs > 1) {
      final var authToken = args[1];
      clientBuilder.authToken(authToken);
    }
    final var client = clientBuilder.createClient();

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
    System.out.println(triggerResponse);

    final var ackResponse = client.acknowledgeEvent(triggerResponse.dedupKey()).join();
    System.out.println(ackResponse);

    final var resolveResponse = client.resolveEvent(triggerResponse.dedupKey()).join();
    System.out.println(resolveResponse);

    final var changeEventResponse = changeEventResponseFuture.join();
    System.out.println(changeEventResponse);
  }
}

package software.sava.incident.pagerduty.event.client;

import org.junit.jupiter.api.Test;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;
import software.sava.incident.pagerduty.event.data.PagerDutyImageRef;
import software.sava.incident.pagerduty.event.data.PagerDutyLinkRef;
import software.sava.incident.pagerduty.event.data.PagerDutySeverity;

import java.time.ZonedDateTime;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.assertEquals;

/// Pins the trigger event's request body without a transport. Property: the body is the
/// routing key, the event's dedup key and payload, then `client` and `client_url`, each
/// omitted when null, empty or blank and serialized as given otherwise, then the images
/// and the links; every caller-supplied string is JSON-escaped. Oracle: hand-written JSON
/// from the field names of the Events API v2 `createV2Event` (`POST /v2/enqueue`) request
/// body and RFC 8259's escapes, not the implementation's output.
final class TriggerJsonTests {

  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2018, 8, 1, 2, 3, 4, 0, UTC);
  private static final String PREFIX = """
      {"event_action":"trigger","routing_key":"rk-1","dedup_key":"dk-1",\
      "payload":{"summary":"trigger summary","source":"trigger-source","severity":"warning",\
      "timestamp":"2018-08-01T02:03:04Z"}""";
  private static final String NAME = "trigger-client";
  private static final String URL = "https://client.example/alerts";

  private static PagerDutyEventPayload event() {
    return PagerDutyEventPayload.build()
        .dedupKey("dk-1")
        .summary("trigger summary")
        .source("trigger-source")
        .severity(PagerDutySeverity.warning)
        .timestamp(TIMESTAMP)
        .create();
  }

  private static String triggerJson(final String clientName, final String clientUrl) {
    return PagerDutyEventClientImpl.triggerJson("rk-1", event(), clientName, clientUrl);
  }

  @Test
  void nullNameAndUrlAreOmitted() {
    assertEquals(PREFIX + "}", triggerJson(null, null));
  }

  @Test
  void emptyNameAndUrlAreOmitted() {
    assertEquals(PREFIX + "}", triggerJson("", ""));
  }

  @Test
  void blankNameAndUrlAreOmitted() {
    assertEquals(PREFIX + "}", triggerJson(" ", " "));
  }

  @Test
  void nameOnly() {
    assertEquals(PREFIX + """
        ,"client":"trigger-client"}""", triggerJson(NAME, null));
  }

  @Test
  void urlOnly() {
    assertEquals(PREFIX + """
        ,"client_url":"https://client.example/alerts"}""", triggerJson(null, URL));
  }

  @Test
  void nameAndUrl() {
    assertEquals(PREFIX + """
        ,"client":"trigger-client","client_url":"https://client.example/alerts"}""", triggerJson(NAME, URL));
  }

  @Test
  void blankNameWithUrl() {
    assertEquals(PREFIX + """
        ,"client_url":"https://client.example/alerts"}""", triggerJson(" ", URL));
  }

  @Test
  void emptyNameWithUrl() {
    assertEquals(PREFIX + """
        ,"client_url":"https://client.example/alerts"}""", triggerJson("", URL));
  }

  @Test
  void nameWithBlankUrl() {
    assertEquals(PREFIX + """
        ,"client":"trigger-client"}""", triggerJson(NAME, " "));
  }

  @Test
  void nameWithEmptyUrl() {
    assertEquals(PREFIX + """
        ,"client":"trigger-client"}""", triggerJson(NAME, ""));
  }

  @Test
  void presentValuesAreNotTrimmed() {
    assertEquals(PREFIX + """
        ,"client":" trigger-client ","client_url":" https://client.example/alerts "}""",
        triggerJson(' ' + NAME + ' ', ' ' + URL + ' '));
  }

  @Test
  void callerStringsAreEscaped() {
    final var event = PagerDutyEventPayload.build()
        .dedupKey("dk\"\\\t1")
        .summary("trigger summary")
        .source("trigger-source")
        .severity(PagerDutySeverity.warning)
        .timestamp(TIMESTAMP)
        .create();
    assertEquals("""
            {"event_action":"trigger","routing_key":"rk\\"\\\\\\t\\u00011","dedup_key":"dk\\"\\\\\\t1",\
            "payload":{"summary":"trigger summary","source":"trigger-source","severity":"warning",\
            "timestamp":"2018-08-01T02:03:04Z"},"client":"cl\\"ie\\\\nt\\t","client_url":"https://u.example/?q=\\"x\\"\\\\"}""",
        PagerDutyEventClientImpl.triggerJson("rk\"\\\t\u00011", event, "cl\"ie\\nt\t", "https://u.example/?q=\"x\"\\")
    );
  }

  @Test
  void clientFieldsPrecedeImagesThenLinks() {
    final var event = PagerDutyEventPayload.build()
        .dedupKey("dk-1")
        .summary("trigger summary")
        .source("trigger-source")
        .severity(PagerDutySeverity.warning)
        .timestamp(TIMESTAMP)
        .link(PagerDutyLinkRef.build().href("https://link.example/1").text("first link").create())
        .link(PagerDutyLinkRef.build().href("https://link.example/2").create())
        .image(PagerDutyImageRef.build().src("https://image.example/1.png").alt("first image").create())
        .image(PagerDutyImageRef.build().src("https://image.example/2.png").create())
        .create();
    assertEquals(PREFIX + """
            ,"client":"trigger-client","client_url":"https://client.example/alerts",\
            "images":[{"src":"https://image.example/1.png","alt":"first image"},{"src":"https://image.example/2.png"}],\
            "links":[{"href":"https://link.example/1","text":"first link"},{"href":"https://link.example/2"}]}""",
        PagerDutyEventClientImpl.triggerJson("rk-1", event, NAME, URL)
    );
  }

  @Test
  void imagesThenLinksWithoutClientFields() {
    final var event = PagerDutyEventPayload.build()
        .dedupKey("dk-1")
        .summary("trigger summary")
        .source("trigger-source")
        .severity(PagerDutySeverity.warning)
        .timestamp(TIMESTAMP)
        .link(PagerDutyLinkRef.build().href("https://link.example/1").create())
        .image(PagerDutyImageRef.build().src("https://image.example/1.png").create())
        .create();
    assertEquals(PREFIX + """
            ,"images":[{"src":"https://image.example/1.png"}],"links":[{"href":"https://link.example/1"}]}""",
        PagerDutyEventClientImpl.triggerJson("rk-1", event, null, " ")
    );
  }
}

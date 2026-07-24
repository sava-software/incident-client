package software.sava.incident.core.api;

import org.junit.jupiter.api.Test;

import java.time.ZonedDateTime;
import java.util.Map;

import static java.time.ZoneOffset.UTC;
import static org.junit.jupiter.api.Assertions.*;

final class IncidentAlertTests {

  private static final ZonedDateTime TIMESTAMP = ZonedDateTime.of(2024, 3, 4, 5, 6, 7, 0, UTC);

  @Test
  void builderRoundTrip() {
    final var alert = IncidentAlert.build()
        .key("key-1")
        .summary("summary-1")
        .details("details-1")
        .severity(IncidentSeverity.CRITICAL)
        .source("host-1")
        .timestamp(TIMESTAMP)
        .customDetail("k1", "v1")
        .customDetail("k2", 2)
        .create();

    assertEquals("key-1", alert.key());
    assertEquals("summary-1", alert.summary());
    assertEquals("details-1", alert.details());
    assertEquals(IncidentSeverity.CRITICAL, alert.severity());
    assertEquals("host-1", alert.source());
    assertEquals(TIMESTAMP, alert.timestamp());
    assertEquals(Map.of("k1", "v1", "k2", 2), alert.customDetails());
    assertThrows(UnsupportedOperationException.class, () -> alert.customDetails().put("k3", "v3"));
  }

  @Test
  void summaryAndSeverityAreRequired() {
    assertThrows(NullPointerException.class, () -> IncidentAlert.build().severity(IncidentSeverity.INFO).create());
    assertThrows(NullPointerException.class, () -> IncidentAlert.build().summary("s").create());
  }

  @Test
  void prototypeCopy() {
    final var alert = IncidentAlert.build()
        .key("key-2")
        .summary("summary-2")
        .details("details-2")
        .severity(IncidentSeverity.WARNING)
        .source("host-2")
        .timestamp(TIMESTAMP)
        .customDetail("k1", "v1")
        .create();

    final var copy = IncidentAlert.build(alert)
        .severity(IncidentSeverity.INFO)
        .customDetail("k2", "v2")
        .create();
    assertEquals("key-2", copy.key());
    assertEquals("summary-2", copy.summary());
    assertEquals("details-2", copy.details());
    assertEquals(IncidentSeverity.INFO, copy.severity());
    assertEquals("host-2", copy.source());
    assertEquals(TIMESTAMP, copy.timestamp());
    assertEquals(Map.of("k1", "v1", "k2", "v2"), copy.customDetails());
    // the original is unchanged
    assertEquals(IncidentSeverity.WARNING, alert.severity());
    assertEquals(Map.of("k1", "v1"), alert.customDetails());

    assertNotNull(IncidentAlert.build(null));
  }

  @Test
  void prototypeWithNullCustomDetails() {
    // a caller-supplied IncidentAlert implementation may return null custom details
    final var prototype = new IncidentAlert() {
      @Override
      public String key() {
        return null;
      }

      @Override
      public String summary() {
        return "proto-summary";
      }

      @Override
      public String details() {
        return null;
      }

      @Override
      public IncidentSeverity severity() {
        return IncidentSeverity.INFO;
      }

      @Override
      public String source() {
        return null;
      }

      @Override
      public ZonedDateTime timestamp() {
        return null;
      }

      @Override
      public Map<String, Object> customDetails() {
        return null;
      }
    };
    final var copy = IncidentAlert.build(prototype).create();
    assertEquals("proto-summary", copy.summary());
    assertEquals(Map.of(), copy.customDetails());
  }

  @Test
  void builderAccessors() {
    final var builder = IncidentAlert.build()
        .key("k")
        .summary("s")
        .details("d")
        .severity(IncidentSeverity.ERROR)
        .source("src")
        .timestamp(TIMESTAMP)
        .customDetail("f", true);
    assertEquals("k", builder.key());
    assertEquals("s", builder.summary());
    assertEquals("d", builder.details());
    assertEquals(IncidentSeverity.ERROR, builder.severity());
    assertEquals("src", builder.source());
    assertEquals(TIMESTAMP, builder.timestamp());
    assertEquals(Map.of("f", true), builder.customDetails());
  }
}

package software.sava.incident.webhook;

import org.junit.jupiter.api.Test;
import software.sava.incident.core.api.IncidentAlert;
import software.sava.incident.core.api.IncidentSeverity;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WebhookFormatsTests {

  private static final ZonedDateTime TIMESTAMP =
      ZonedDateTime.of(2026, 7, 26, 12, 30, 45, 0, ZoneOffset.UTC);

  private static IncidentAlert fullAlert() {
    return IncidentAlert.build()
        .key("dedup-1")
        .summary("db \"primary\" down")
        .details("line one\nline two")
        .severity(IncidentSeverity.CRITICAL)
        .source("validator-01.example.com")
        .timestamp(TIMESTAMP)
        .customDetail("region", "us-east-1")
        .customDetail("slot", 350000000L)
        .customDetail("degraded", true)
        .customDetail("ratio", new BigDecimal("1E+3"))
        .customDetail("epoch", new BigInteger("512"))
        .customDetail("missing", null)
        .create();
  }

  @Test
  void genericJsonFullAlert() {
    assertEquals("""
            {"summary":"db \\"primary\\" down","severity":"CRITICAL","key":"dedup-1","source":"validator-01.example.com","timestamp":"2026-07-26T12:30:45Z","details":"line one\\nline two","customDetails":{"region":"us-east-1","slot":350000000,"degraded":true,"ratio":1000,"epoch":512,"missing":null}}""",
        WebhookFormats.GENERIC_JSON.render(fullAlert())
    );
  }

  @Test
  void genericJsonMinimalAlert() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .severity(IncidentSeverity.INFO)
        .create();
    assertEquals("""
            {"summary":"summary-1","severity":"INFO"}""",
        WebhookFormats.GENERIC_JSON.render(alert)
    );
  }

  @Test
  void genericJsonOmitsBlankOptionals() {
    final var alert = IncidentAlert.build()
        .key("")
        .summary("summary-1")
        .details(" ")
        .severity(IncidentSeverity.WARNING)
        .source("")
        .create();
    assertEquals("""
            {"summary":"summary-1","severity":"WARNING"}""",
        WebhookFormats.GENERIC_JSON.render(alert)
    );
  }

  @Test
  void genericJsonRegionZoneReducesToOffset() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .severity(IncidentSeverity.ERROR)
        .timestamp(ZonedDateTime.of(2026, 1, 15, 7, 8, 9, 0, ZoneId.of("America/New_York")))
        .create();
    assertEquals("""
            {"summary":"summary-1","severity":"ERROR","timestamp":"2026-01-15T07:08:09-05:00"}""",
        WebhookFormats.GENERIC_JSON.render(alert)
    );
  }

  @Test
  void genericJsonEscapesCustomDetailKeys() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .severity(IncidentSeverity.INFO)
        .customDetail("a\"b", "c\\d")
        .create();
    assertEquals("""
            {"summary":"summary-1","severity":"INFO","customDetails":{"a\\"b":"c\\\\d"}}""",
        WebhookFormats.GENERIC_JSON.render(alert)
    );
  }

  @Test
  void slackTextFullAlert() {
    assertEquals("""
            {"text":"[CRITICAL] db \\"primary\\" down\\nline one\\nline two\\nSource: validator-01.example.com\\nTime: 2026-07-26T12:30:45Z\\nKey: dedup-1\\nregion: us-east-1\\nslot: 350000000\\ndegraded: true\\nratio: 1E+3\\nepoch: 512\\nmissing: null"}""",
        WebhookFormats.SLACK_TEXT.render(fullAlert())
    );
  }

  @Test
  void slackTextMinimalAlert() {
    final var alert = IncidentAlert.build()
        .summary("summary-1")
        .severity(IncidentSeverity.INFO)
        .create();
    assertEquals("""
            {"text":"[INFO] summary-1"}""",
        WebhookFormats.SLACK_TEXT.render(alert)
    );
  }

  @Test
  void slackTextEscapesEntities() {
    final var alert = IncidentAlert.build()
        .summary("a & b <tag> c")
        .details("x > y & z")
        .severity(IncidentSeverity.ERROR)
        .create();
    assertEquals("[ERROR] a &amp; b &lt;tag&gt; c\nx &gt; y &amp; z",
        WebhookFormats.renderText(alert));
    assertEquals("""
            {"text":"[ERROR] a &amp; b &lt;tag&gt; c\\nx &gt; y &amp; z"}""",
        WebhookFormats.SLACK_TEXT.render(alert)
    );
  }

  /// [IncidentAlert] is an interface; unlike the builder, a caller's own implementation
  /// may return null customDetails, which both renderers must tolerate.
  @Test
  void nullCustomDetailsFromCustomAlertImplementation() {
    final var alert = new IncidentAlert() {
      @Override
      public String key() {
        return null;
      }

      @Override
      public String summary() {
        return "summary-1";
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
      public java.util.Map<String, Object> customDetails() {
        return null;
      }
    };
    assertEquals("""
            {"summary":"summary-1","severity":"INFO"}""",
        WebhookFormats.GENERIC_JSON.render(alert)
    );
    assertEquals("[INFO] summary-1", WebhookFormats.renderText(alert));
  }

  @Test
  void slackTextOmitsBlankOptionals() {
    final var alert = IncidentAlert.build()
        .key(" ")
        .summary("summary-1")
        .details("")
        .severity(IncidentSeverity.WARNING)
        .source(" ")
        .create();
    assertEquals("[WARNING] summary-1", WebhookFormats.renderText(alert));
  }
}

package software.sava.incident.pagerduty.event.data;

public interface PagerDutyLinkRef {

  static Builder build() {
    return new PagerDutyLinkRefVal.PagerDutyLinkRefBuilder();
  }

  String href();

  String text();

  default String toJson() {
    final var text = text();
    if (text == null || text.isBlank()) {
      return String.format("""
          {"href":"%s"}""", href());
    } else {
      return String.format("""
          {"href":"%s","text":"%s"}""", href(), text);
    }
  }

  interface Builder extends PagerDutyLinkRef {

    PagerDutyLinkRef create();

    Builder href(final String href);

    Builder text(final String text);
  }
}

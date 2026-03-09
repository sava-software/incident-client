package software.sava.incident.pagerduty.event.data;

public interface PagerDutyImageRef {

  static Builder build() {
    return new PagerDutyImageRefVal.PagerDutyImageRefBuilder();
  }

  String src();

  String href();

  String alt();

  default String toJson() {
    final var href = href();
    final var alt = alt();
    if (href == null || href.isBlank()) {
      if (alt == null || alt.isBlank()) {
        return String.format("""
            {"src":"%s"}""", src());
      } else {
        return String.format("""
            {"src":"%s","alt":"%s"}""", src(), alt);
      }
    } else if (alt == null || alt.isBlank()) {
      return String.format("""
          {"src":"%s","href":"%s"}""", src(), href);
    } else {
      return String.format("""
          {"src":"%s","href":"%s","alt":"%s"}""", src(), href, alt);
    }
  }

  interface Builder extends PagerDutyImageRef {

    PagerDutyImageRef create();

    Builder src(final String src);

    Builder href(final String href);

    Builder alt(final String alt);
  }
}

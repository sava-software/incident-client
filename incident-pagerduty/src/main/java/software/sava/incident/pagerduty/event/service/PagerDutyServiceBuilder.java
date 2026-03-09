package software.sava.incident.pagerduty.event.service;

import software.sava.incident.pagerduty.event.client.PagerDutyEventClient;
import software.sava.incident.pagerduty.event.data.PagerDutyEventPayload;

final class PagerDutyServiceBuilder implements PagerDutyService.Builder {

  private PagerDutyEventClient client;
  private PagerDutyEventPayload eventPrototype;

  PagerDutyServiceBuilder() {
  }

  @Override
  public PagerDutyService create() {
    return new PagerDutyServiceVal(client, eventPrototype);
  }

  @Override
  public PagerDutyService.Builder client(final PagerDutyEventClient client) {
    this.client = client;
    return this;
  }

  @Override
  public PagerDutyService.Builder eventPrototype(final PagerDutyEventPayload eventPrototype) {
    this.eventPrototype = eventPrototype;
    return this;
  }
}

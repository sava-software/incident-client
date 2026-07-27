package software.sava.incident.webhook;

/// `ServiceLoader` factory creating a [WebhookIncidentClient] that POSTs
/// [WebhookFormats#SLACK_TEXT] messages to a Slack incoming-webhook URL; registered under
/// provider id `slack`. The webhook URL is the credential — configure it as `endpoint`.
public final class SlackWebhookIncidentClientFactory extends BaseWebhookIncidentClientFactory {

  public SlackWebhookIncidentClientFactory() {
    super("slack", WebhookFormats.SLACK_TEXT);
  }
}

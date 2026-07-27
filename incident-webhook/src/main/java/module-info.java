module software.sava.incident_webhook {
  requires java.net.http;

  requires transitive systems.comodal.json_iterator;
  requires software.sava.rpc;

  requires transitive software.sava.incident_core;

  exports software.sava.incident.webhook;
  exports software.sava.incident.webhook.config;
  exports software.sava.incident.webhook.exceptions;

  provides software.sava.incident.core.api.IncidentClientFactory with
      software.sava.incident.webhook.WebhookIncidentClientFactory,
      software.sava.incident.webhook.SlackWebhookIncidentClientFactory,
      software.sava.incident.webhook.TelegramWebhookIncidentClientFactory;
}

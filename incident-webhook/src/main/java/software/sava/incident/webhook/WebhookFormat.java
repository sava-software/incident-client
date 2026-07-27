package software.sava.incident.webhook;

import software.sava.incident.core.api.IncidentAlert;

/// Renders an [IncidentAlert] as the JSON body POSTed to a webhook endpoint. This is the
/// only seam that varies between webhook receivers — transport, configuration, and error
/// handling are shared — so supporting another product is one implementation of this
/// interface; see [WebhookFormats] for the built-in formats.
public interface WebhookFormat {

  /// Renders `alert` as a complete JSON document. Every caller-supplied string must be
  /// escaped on its way in; the result is sent verbatim as an `application/json` body.
  String render(final IncidentAlert alert);
}

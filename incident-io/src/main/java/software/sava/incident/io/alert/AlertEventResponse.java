package software.sava.incident.io.alert;

/// The 202 body of `Alert Events V2#CreateHTTP` (`AlertEventsCreateHTTPResultV2`): the event
/// was accepted for processing. The schema requires all three members; one a response omits
/// reads as null.
public interface AlertEventResponse {

  /// Status of the event, e.g. `success`.
  String status();

  /// Human-readable detail about the event, e.g. `Event accepted for processing`.
  String message();

  /// The deduplication key the event was processed with.
  String deduplicationKey();
}

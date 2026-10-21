package io.archintel.connector.http;

/** An upstream API failed after retries. status is the HTTP status, or -1 for transport failures. */
public class ExternalApiException extends RuntimeException {
    private final int status;

    public ExternalApiException(String message, int status) { super(message); this.status = status; }
    public ExternalApiException(String message, int status, Throwable cause) { super(message, cause); this.status = status; }

    public int status() { return status; }
}

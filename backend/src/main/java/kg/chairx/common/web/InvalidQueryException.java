package kg.chairx.common.web;

/** Client-supplied filters or pagination parameters are invalid (HTTP 400). */
public class InvalidQueryException extends RuntimeException {
    public InvalidQueryException(String message) { super(message); }
}

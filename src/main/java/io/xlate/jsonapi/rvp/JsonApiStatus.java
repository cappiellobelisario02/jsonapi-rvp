package io.xlate.jsonapi.rvp;

import jakarta.ws.rs.core.Response.Status.Family;
import jakarta.ws.rs.core.Response.StatusType;

public enum JsonApiStatus implements StatusType {

    /**
     * 422 Unprocessable Entity, see <a href="https://tools.ietf.org/html/rfc4918#section-11.2">WebDAV documentation</a>.
     */
    UNPROCESSABLE_ENTITY(422, "Unprocessable Entity");

    private final int code;
    private final String reason;
    private final Family family;

    JsonApiStatus(final int statusCode, final String reasonPhrase) {
        this.code = statusCode;
        this.reason = reasonPhrase;
        this.family = Family.familyOf(statusCode);
    }

    /**
     * Get the class of status code.
     *
     * @return the class of status code.
     */
    @Override
    public Family getFamily() {
        return family;
    }

    /**
     * Get the associated status code.
     *
     * @return the status code.
     */
    @Override
    public int getStatusCode() {
        return code;
    }

    /**
     * Get the reason phrase.
     *
     * @return the reason phrase.
     */
    @Override
    public String getReasonPhrase() {
        return toString();
    }

    /**
     * Get the reason phrase.
     *
     * @return the reason phrase.
     */
    @Override
    public String toString() {
        return reason;
    }

    /**
     * Convert a numerical status code into the corresponding Status.
     *
     * @param statusCode the numerical status code.
     * @return the matching JsonApiStatus or null is no matching Status is defined.
     */
    public static JsonApiStatus fromStatusCode(final int statusCode) {
        for (JsonApiStatus s : JsonApiStatus.values()) {
            if (s.code == statusCode) {
                return s;
            }
        }
        return null;
    }
}

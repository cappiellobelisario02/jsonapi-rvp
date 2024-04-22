package io.xlate.jsonapi.rvp.internal.rs.entity;

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.json.JsonObjectBuilder;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response.StatusType;

public class JsonApiError {

    public static final String DATA_ATTRIBUTES_POINTER = "/data/attributes";
    public static final String DATA_RELATIONSHIPS_POINTER = "/data/relationships";

    private final StatusType status;
    private final String code;
    private final String title;
    private final String detail;
    private final Source source;

    public static JsonApiError forParameterViolation(ConstraintViolation<?> violation) {
        final Source source = Source.forParameter(violation.getPropertyPath());
        final String message = violation.getMessage();
        return new JsonApiError("Invalid Query Parameter", message, source);
    }

    public JsonApiError(StatusType status, String code, String title, String detail, Source source) {
        this.status = status;
        this.code = code;
        this.title = title;
        this.detail = detail;
        this.source = source;
    }

    public JsonApiError(StatusType status, String detail, Source source) {
        this(status, null, status.getReasonPhrase(), detail, source);
    }

    public JsonApiError(StatusType status, String detail) {
        this(status, detail, null);
    }

    public JsonApiError(StatusType status, String title, String detail, Source source) {
        this(status, null, title, detail, source);
    }

    public JsonApiError(String title, String detail, Source source) {
        this(null, null, title, detail, source);
    }

    public static String attributePointer(String attributeName) {
        return DATA_ATTRIBUTES_POINTER + '/' + attributeName;
    }

    public static String relationshipPointer(String relationshipName) {
        return DATA_RELATIONSHIPS_POINTER + '/' + relationshipName;
    }

    public JsonObject toJson() {
        JsonObjectBuilder builder = Json.createObjectBuilder();

        if (status != null) {
            add(builder, "status", String.valueOf(status.getStatusCode()));
        }
        add(builder, "code", code);
        add(builder, "title", title);
        add(builder, "detail", detail);

        if (source != null) {
            builder.add("source", source.toJson());
        }

        return builder.build();
    }

    static void add(JsonObjectBuilder builder, String name, String value) {
        if (value != null) {
            builder.add(name, value);
        }
    }

    public StatusType getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    public String getTitle() {
        return title;
    }

    public String getDetail() {
        return detail;
    }

    public Source getSource() {
        return source;
    }

    public static class Source {
        private final String pointer;
        private final String parameter;

        public static Source forPointer(String pointer) {
            return new Source(pointer, null);
        }

        public static Source forParameter(Path parameter) {
            return new Source(null, parameter.iterator().next().getName());
        }

        public Source(String pointer, String parameter) {
            this.pointer = pointer;
            this.parameter = parameter;
        }

        public JsonObject toJson() {
            JsonObjectBuilder builder = Json.createObjectBuilder();
            add(builder, "pointer", pointer);
            add(builder, "parameter", parameter);
            return builder.build();
        }

    }
}

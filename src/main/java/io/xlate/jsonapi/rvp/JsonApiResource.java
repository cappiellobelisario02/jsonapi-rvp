package io.xlate.jsonapi.rvp;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;

import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.json.JsonObject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.EntityTag;
import jakarta.ws.rs.core.Request;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.core.UriBuilder;
import jakarta.ws.rs.core.UriInfo;

import io.xlate.jsonapi.rvp.internal.DefaultJsonApiHandler;
import io.xlate.jsonapi.rvp.internal.JsonApiErrorException;
import io.xlate.jsonapi.rvp.internal.JsonApiHandlerChain;
import io.xlate.jsonapi.rvp.internal.persistence.boundary.PersistenceController;
import io.xlate.jsonapi.rvp.internal.persistence.entity.EntityMeta;
import io.xlate.jsonapi.rvp.internal.persistence.entity.EntityMetamodel;
import io.xlate.jsonapi.rvp.internal.rs.boundary.Responses;
import io.xlate.jsonapi.rvp.internal.rs.entity.InternalContext;
import io.xlate.jsonapi.rvp.internal.rs.entity.InternalQuery;
import io.xlate.jsonapi.rvp.internal.rs.entity.JsonApiRequest;
import io.xlate.jsonapi.rvp.internal.validation.boundary.TransactionalValidator;

@Consumes(JsonApiMediaType.APPLICATION_JSONAPI)
@Produces(JsonApiMediaType.APPLICATION_JSONAPI)
public abstract class JsonApiResource {

    private static final Logger logger = Logger.getLogger(JsonApiResource.class.getName());
    private static final String CLIENT_PATH = "internal/rs/boundary/client.js";
    private static final JsonApiHandler<?> DEFAULT_HANDLER = new DefaultJsonApiHandler();

    @Inject
    @Any
    Instance<JsonApiHandler<?>> handlers;

    @Context
    protected Request request;

    @Context
    protected UriInfo uriInfo;

    @Context
    protected SecurityContext security;

    @PersistenceContext
    protected EntityManager persistenceContext;

    @Inject
    protected Validator validator;

    @Inject
    TransactionalValidator txValidator;

    Date initializationDate = new Date();
    CacheControl cacheControl = new CacheControl();

    private Class<?> resourceClass;
    private EntityMetamodel model;
    private PersistenceController persistence;

    private Map<URI, String> clients = new ConcurrentHashMap<>(5);

    protected void initialize(Set<JsonApiResourceType<?>> resourceTypes) {
        resourceClass = this.getClass();

        while (resourceClass != null && !resourceClass.isAnnotationPresent(Path.class)) {
            resourceClass = resourceClass.getSuperclass();
        }

        if (resourceClass == null) {
            throw new IllegalStateException("Resource class missing @Path annotation");
        }

        model = new EntityMetamodel(resourceClass, resourceTypes, persistenceContext.getMetamodel());
        persistence = new PersistenceController(persistenceContext, model, txValidator);
    }

    protected JsonApiResource() {
        cacheControl.setPrivate(true);
    }

    private Set<ConstraintViolation<InternalQuery>> validateParameters(InternalQuery params) {
        return Collections.unmodifiableSet(validator.validate(params));
    }

    @SuppressWarnings("java:S1452") // Suppress Sonar warnings regarding missing generic types
    private Set<ConstraintViolation<?>> validateEntity(String resourceType, String id, JsonObject input) {
        JsonApiRequest jsonApiRequest = new JsonApiRequest(request.getMethod(),
                                                           model,
                                                           model.getEntityMeta(resourceType),
                                                           id,
                                                           input);

        return Collections.unmodifiableSet(validator.validate(jsonApiRequest));
    }

    boolean isValidResourceAndMethodAllowed(InternalContext context, EntityMeta meta, String id) {
        if (meta == null || (id != null && !isValidId(meta, id))) {
            Responses.notFound(context);
            return false;
        } else if (!meta.isMethodAllowed(request.getMethod())) {
            Responses.methodNotAllowed(context);
            return false;
        }

        return true;
    }

    static String generateClientModule(URI uri, Class<?> resourceClass) {
        StringBuilder buffer = new StringBuilder();

        try {
            try (Reader prototype = new InputStreamReader(JsonApiResource.class.getResourceAsStream(CLIENT_PATH))) {
                var builder = UriBuilder.fromUri(uri);
                builder.replacePath("").path(resourceClass);
                buffer.append(String.format("const baseAdminUrl = '%s';%n%n", builder.build().toString()));
                int data;

                while ((data = prototype.read()) > -1) {
                    buffer.append((char) data);
                }
            }
        } catch (IOException e) {
            throw new InternalServerErrorException("Client module not available", e);
        }

        return buffer.toString();
    }

    @GET
    @Path("client.js")
    @Produces("application/javascript")
    public Response getClient() {
        String client = clients.computeIfAbsent(uriInfo.getRequestUri(), uri -> generateClientModule(uri, this.resourceClass));
        EntityTag etag = new EntityTag(Integer.toString(client.hashCode()));
        ResponseBuilder builder;
        builder = request.evaluatePreconditions(initializationDate, etag);

        if (builder == null) {
            builder = Response.ok(client);
            builder.tag(etag);
            builder.lastModified(initializationDate);
        }

        builder.cacheControl(cacheControl);

        return builder.build();
    }

    @POST
    @Path("{resource-type}")
    public Response create(@PathParam("resource-type") String resourceType, JsonObject input) {
        InternalContext context = new InternalContext(request, uriInfo, security, resourceType, input);
        return writeEntity(context, persistence::create, response -> Responses.created(context, resourceClass, response));
    }

    @GET
    @Path("{resource-type}")
    public Response index(@PathParam("resource-type") String resourceType) {
        InternalContext context = new InternalContext(request, uriInfo, security, resourceType);
        JsonApiHandler<?> handler = findHandler(resourceType, request.getMethod());

        try {
            EntityMeta meta = model.getEntityMeta(resourceType);

            if (isValidResourceAndMethodAllowed(context, meta, null)) {
                fetch(context, meta, handler);
            }
        } catch (JsonApiErrorException e) {
            Responses.error(context, e);
        } catch (Exception e) {
            Responses.internalServerError(context, e);
        }

        handler.beforeResponse(context);
        return context.getResponseBuilder().build();
    }

    @GET
    @Path("{resource-type}/{id}")
    public Response read(@PathParam("resource-type") String resourceType,
                         @PathParam("id") final String id) {

        return read(new InternalContext(request, uriInfo, security, resourceType, id));
    }

    @GET
    @Path("{resource-type}/{id}/{relationship-name}")
    public Response readRelated(@PathParam("resource-type") String resourceType,
                                @PathParam("id") final String id,
                                @PathParam("relationship-name") String relationshipName) {

        InternalContext context = new InternalContext(request, uriInfo, security, resourceType, id, relationshipName);
        JsonApiHandler<?> handler = findHandler(context.getResourceType(), request.getMethod());

        try {
            EntityMeta meta = model.getEntityMeta(context.getResourceType());
            EntityMeta relatedMeta = model.getEntityMeta(meta.getRelatedEntityClass(relationshipName));

            if (isValidResourceAndMethodAllowed(context, meta, context.getResourceId()) && isValidResourceAndMethodAllowed(context, relatedMeta, null)) {
                fetch(context, meta, handler);
            }
        } catch (JsonApiErrorException e) {
            Responses.error(context, e);
        } catch (Exception e) {
            Responses.internalServerError(context, e);
        }

        handler.beforeResponse(context);
        return context.getResponseBuilder().build();
    }

    Response read(InternalContext context) {
        JsonApiHandler<?> handler = findHandler(context.getResourceType(), request.getMethod());

        try {
            EntityMeta meta = model.getEntityMeta(context.getResourceType());

            if (isValidResourceAndMethodAllowed(context, meta, context.getResourceId())) {
                fetch(context, meta, handler);
            }
        } catch (JsonApiErrorException e) {
            Responses.error(context, e);
        } catch (Exception e) {
            Responses.internalServerError(context, e);
        }

        handler.beforeResponse(context);
        return context.getResponseBuilder().build();
    }

    void fetch(InternalContext context, EntityMeta meta, JsonApiHandler<?> handler) {
        InternalQuery params = new InternalQuery(this.model, meta, context.getResourceId(), context.getRelationshipName(), context.getUriInfo());
        context.setQuery(params);

        handler.onRequest(context);

        Set<ConstraintViolation<InternalQuery>> violations = validateParameters(params);

        if (violations.isEmpty()) {
            JsonObject response = persistence.fetch(context, handler);

            if (!context.hasResponse()) {
                if (response != null) {
                    Responses.ok(context, cacheControl, response);
                } else {
                    Responses.notFound(context);
                }
            }
        } else {
            Responses.badRequest(context, violations);
        }
    }

    @GET
    @Path("{resource-type}/{id}/relationships/{relationship-name}")
    public Response readRelationship(@PathParam("resource-type") String resourceType,
                                     @PathParam("id") final String id,
                                     @PathParam("relationship-name") String relationshipName) {

        InternalContext context = new InternalContext(request, uriInfo, security, resourceType, id, relationshipName);
        JsonApiHandler<?> handler = findHandler(resourceType, request.getMethod());

        try {
            EntityMeta meta = model.getEntityMeta(resourceType);

            if (isValidResourceAndMethodAllowed(context, meta, id)) {
                JsonObject response = persistence.getRelationships(context);

                if (response != null) {
                    Responses.ok(context, cacheControl, response);
                } else {
                    Responses.notFound(context);
                }
            }
        } catch (JsonApiErrorException e) {
            Responses.error(context, e);
        } catch (Exception e) {
            Responses.internalServerError(context, e);
        }

        handler.beforeResponse(context);
        return context.getResponseBuilder().build();
    }

    @PATCH
    @Path("{resource-type}/{id}/relationships/{relationship-name}")
    public Response replaceRelationship(@PathParam("resource-type") String resourceType,
                                        @PathParam("id") final String id,
                                        @PathParam("relationship-name") String relationshipName,
                                        final JsonObject input) {
        // TODO: implement relationship updates
        return Responses.notImplemented().build();
    }

    @POST
    @Path("{resource-type}/{id}/relationships/{relationship-name}")
    public Response addRelationship(@PathParam("resource-type") String resourceType,
                                    @PathParam("id") final String id,
                                    @PathParam("relationship-name") String relationshipName,
                                    final JsonObject input) {
        // TODO: implement relationship updates
        return Responses.notImplemented().build();
    }

    @DELETE
    @Path("{resource-type}/{id}/relationships/{relationship-name}")
    public Response deleteRelationship(@PathParam("resource-type") String resourceType,
                                       @PathParam("id") final String id,
                                       @PathParam("relationship-name") String relationshipName,
                                       final JsonObject input) {
        // TODO: implement relationship updates
        return Responses.notImplemented().build();
    }

    @PATCH
    @Path("{resource-type}/{id}")
    public Response patch(@PathParam("resource-type") String resourceType,
                          @PathParam("id") String id,
                          final JsonObject input) {
        return update(resourceType, id, input);
    }

    @PUT
    @Path("{resource-type}/{id}")
    public Response update(@PathParam("resource-type") String resourceType,
                           @PathParam("id") String id,
                           final JsonObject input) {

        InternalContext context = new InternalContext(request, uriInfo, security, resourceType, id, input);
        return writeEntity(context, persistence::update, response -> Responses.ok(context, cacheControl, response));
    }

    @DELETE
    @Path("{resource-type}/{id}")
    public Response delete(@PathParam("resource-type") String resourceType, @PathParam("id") final String id) {
        InternalContext context = new InternalContext(request, uriInfo, security, resourceType, id);
        JsonApiHandler<?> handler = findHandler(resourceType, request.getMethod());

        try {
            EntityMeta meta = model.getEntityMeta(resourceType);

            if (isValidResourceAndMethodAllowed(context, meta, id)) {
                handler.onRequest(context);

                if (persistence.delete(context, handler)) {
                    if (!context.hasResponse()) {
                        context.setResponseBuilder(Response.noContent());
                    }
                } else {
                    Responses.notFound(context);
                }
            }
        } catch (JsonApiErrorException e) {
            Responses.error(context, e);
        } catch (Exception e) {
            Responses.internalServerError(context, e);
        }

        handler.beforeResponse(context);
        return context.getResponseBuilder().build();
    }

    Response writeEntity(InternalContext context,
                         BiFunction<InternalContext, JsonApiHandler<?>, JsonObject> persist,
                         Consumer<JsonObject> responder) {

        JsonApiHandler<?> handler = findHandler(context.getResourceType(), request.getMethod());

        try {
            EntityMeta meta = model.getEntityMeta(context.getResourceType());

            if (isValidResourceAndMethodAllowed(context, meta, context.getResourceId())) {
                context.setEntityMeta(meta);
                handler.onRequest(context);
                Set<ConstraintViolation<?>> violations = validateEntity(context.getResourceType(), null, context.getRequestEntity());
                handler.afterValidation(context, violations);

                if (violations.isEmpty()) {
                    JsonObject response = persist.apply(context, handler);

                    if (!context.hasResponse()) {
                        if (response != null) {
                            responder.accept(response);
                        } else {
                            Responses.notFound(context);
                        }
                    }
                } else {
                    Responses.unprocessableEntity(context, "Invalid JSON API Document Structure", violations);
                }
            }
        } catch (ConstraintViolationException e) {
            Responses.unprocessableEntity(context, "Invalid Input", e.getConstraintViolations());
        } catch (JsonApiErrorException e) {
            Responses.error(context, e);
        } catch (Exception e) {
            Responses.internalServerError(context, e);
        }

        handler.beforeResponse(context);
        return context.getResponseBuilder().build();
    }

    boolean isValidId(EntityMeta meta, String id) {
        try {
            return meta != null && meta.readId(id) != null;
        } catch (Exception e) {
            logger.log(Level.FINER, e, () -> "Exception reading id value `" + id + "`");
            return false;
        }
    }

    @SuppressWarnings("java:S1452") // Suppress Sonar warnings regarding missing generic types
    JsonApiHandler<?> findHandler(String resourceType, String httpMethod) {
        List<JsonApiHandler<?>> available = new ArrayList<>(2);

        for (JsonApiHandler<?> handler : handlers) {
            if (handler.isHandler(resourceType, httpMethod)) {
                available.add(handler);
            }
        }

        if (!available.isEmpty()) {
            return new JsonApiHandlerChain(available);
        }

        return DEFAULT_HANDLER;
    }
}

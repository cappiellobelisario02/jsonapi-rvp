package io.xlate.jsonapi.rvp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.StringReader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

import javax.enterprise.inject.Instance;
import javax.json.Json;
import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;
import javax.json.JsonWriter;
import javax.json.JsonWriterFactory;
import javax.json.stream.JsonGenerator;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.validation.Validation;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Request;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.SecurityContext;

import org.jboss.resteasy.specimpl.ResteasyUriInfo;
import org.json.JSONException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvFileSource;
import org.mockito.Mockito;
import org.skyscreamer.jsonassert.JSONAssert;
import org.skyscreamer.jsonassert.JSONCompareMode;

import io.xlate.jsonapi.rvp.internal.DefaultJsonApiHandler;
import io.xlate.jsonapi.rvp.internal.validation.boundary.TransactionalValidator;
import io.xlate.jsonapi.rvp.test.entity.Comment;
import io.xlate.jsonapi.rvp.test.entity.Post;
import io.xlate.jsonapi.rvp.test.entity.ReadOnlyCode;

class JsonApiResourceTest {

    @Path("/test")
    static class ApiImpl extends JsonApiResource {
    }

    EntityManagerFactory emf;
    EntityManager em;
    JsonApiResource target;
    JsonApiHandler<?> defaultHandler = new DefaultJsonApiHandler();

    Iterator<JsonApiHandler<?>> handlerIterator() {
        List<JsonApiHandler<?>> handlers = Arrays.asList(defaultHandler);
        return handlers.iterator();
    }

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        emf = Persistence.createEntityManagerFactory("test");
        em = emf.createEntityManager();
        target = new ApiImpl();
        target.persistenceContext = em;
        target.validator = Validation.buildDefaultValidatorFactory().getValidator();
        target.handlers = Mockito.mock(Instance.class);
        target.request = Mockito.mock(Request.class);
        target.security = Mockito.mock(SecurityContext.class);
        target.txValidator = new TransactionalValidator(target.validator);

        Mockito.when(target.handlers.iterator()).thenReturn(handlerIterator());

        Set<JsonApiResourceType<?>> resourceTypes = new HashSet<>();
        resourceTypes.add(JsonApiResourceType.define("posts", Post.class).build());
        resourceTypes.add(JsonApiResourceType.define("comments", Comment.class).build());
        resourceTypes.add(JsonApiResourceType.define("readonly-codes", ReadOnlyCode.class).methods(GET.class).build());
        target.initialize(resourceTypes);
    }

    @AfterEach
    void tearDown() {
        em.close();
        emf.close();
    }

    void executeDml(String jsonDml) {
        if (!jsonDml.isBlank()) {
            JsonArray commands = Json.createReader(new StringReader(jsonDml)).readArray();
            var tx = em.getTransaction();
            tx.begin();

            for (JsonValue entry : commands) {
                JsonObject command = entry.asJsonObject();
                em.createNativeQuery(command.getString("sql")).executeUpdate();
            }

            tx.commit();
        }
    }

    JsonObject readObject(String requestBody) {
        return Json.createReader(new StringReader(requestBody.replace('\'', '"'))).readObject();
    }

    void assertResponseEquals(int expectedStatus, int actualStatus, String expectedEntity, String actualEntity) throws JSONException {
        try {
            assertEquals(expectedStatus, actualStatus);
            JSONAssert.assertEquals(expectedEntity, actualEntity, JSONCompareMode.NON_EXTENSIBLE);
        } catch (Throwable t) {
            Map<String, Object> map = new HashMap<>();
            map.put(JsonGenerator.PRETTY_PRINTING, true);
            JsonWriterFactory writerFactory = Json.createWriterFactory(map);
            JsonWriter jsonWriter = writerFactory.createWriter(System.out);
            jsonWriter.writeObject(Json.createReader(new StringReader(actualEntity)).readObject());
            jsonWriter.close();
            throw t;
        }
    }

    void testResourceMethod(String jsonDml,
                            String requestUri,
                            String requestMethod,
                            int expectedStatus,
                            String expectedResponse,
                            Supplier<Response> responseSupplier)
            throws JSONException {

        executeDml(jsonDml);

        Mockito.when(target.request.getMethod()).thenReturn(requestMethod);
        target.uriInfo = new ResteasyUriInfo(requestUri, "/");
        Response response;

        var tx = em.getTransaction();

        try {
            tx.begin();
            response = responseSupplier.get();
            tx.commit();
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }

        assertNotNull(response);

        String responseEntity = String.valueOf(response.getEntity());
        assertResponseEquals(expectedStatus, response.getStatus(), expectedResponse, responseEntity);
    }

    @ParameterizedTest
    @CsvFileSource(
            delimiter = '|',
            lineSeparator = "@\n",
            files = {
                      "src/test/resources/create-post.txt",
                      "src/test/resources/create-post-invalid.txt"})
    void testCreatePost(String title,
                        String jsonDml,
                        String requestUri,
                        String resourceType,
                        String requestBody,
                        int expectedStatus,
                        String expectedResponse)
            throws JSONException {

        testResourceMethod(jsonDml,
                           requestUri,
                           "POST",
                           expectedStatus,
                           expectedResponse,
                           () -> target.create(resourceType, readObject(requestBody)));
    }

    @ParameterizedTest
    @CsvFileSource(delimiter = '|', lineSeparator = "@\n", files = "src/test/resources/index-get.txt")
    void testIndexGet(String title,
                      String jsonDml,
                      String requestUri,
                      String resourceType,
                      int expectedStatus,
                      String expectedResponse)
            throws JSONException {

        testResourceMethod(jsonDml,
                           requestUri,
                           "GET",
                           expectedStatus,
                           expectedResponse,
                           () -> target.index(resourceType));
    }

    @ParameterizedTest
    @CsvFileSource(delimiter = '|', lineSeparator = "@\n", files = "src/test/resources/read-get.txt")
    void testReadGet(String title,
                     String jsonDml,
                     String requestUri,
                     String resourceType,
                     String resourceId,
                     int expectedStatus,
                     String expectedResponse)
            throws JSONException {

        testResourceMethod(jsonDml,
                           requestUri,
                           "GET",
                           expectedStatus,
                           expectedResponse,
                           () -> target.read(resourceType, resourceId));
    }

    @ParameterizedTest
    @CsvFileSource(delimiter = '|', lineSeparator = "@\n", files = "src/test/resources/read-relationship-get.txt")
    void testReadRelationshipGet(String title,
                     String jsonDml,
                     String requestUri,
                     String resourceType,
                     String resourceId,
                     String relationshipName,
                     int expectedStatus,
                     String expectedResponse)
            throws JSONException {

        testResourceMethod(jsonDml,
                           requestUri,
                           "GET",
                           expectedStatus,
                           expectedResponse,
                           () -> target.readRelationship(resourceType, resourceId, relationshipName));
    }

    @ParameterizedTest
    @CsvFileSource(
            delimiter = '|',
            lineSeparator = "@\n",
            files = {
                      "src/test/resources/update-patch.txt",
                      "src/test/resources/update-patch-invalid.txt" })
    void testUpdatePatch(String title,
                         String jsonDml,
                         String requestUri,
                         String resourceType,
                         String resourceId,
                         String requestBody,
                         int expectedStatus,
                         String expectedResponse)
            throws JSONException {

        testResourceMethod(jsonDml,
                           requestUri,
                           "PATCH",
                           expectedStatus,
                           expectedResponse,
                           () -> target.patch(resourceType, resourceId, readObject(requestBody)));
    }
}
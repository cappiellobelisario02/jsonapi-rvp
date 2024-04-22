package io.xlate.jsonapi.rvp.internal.persistence.entity;

import java.beans.BeanInfo;
import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.beans.PropertyDescriptor;
import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.Attribute.PersistentAttributeType;
import jakarta.persistence.metamodel.Bindable;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import jakarta.persistence.metamodel.PluralAttribute;
import jakarta.persistence.metamodel.SingularAttribute;
import jakarta.ws.rs.HttpMethod;
import jakarta.ws.rs.core.Response.Status;

import io.xlate.jsonapi.rvp.JsonApiResourceType;
import io.xlate.jsonapi.rvp.internal.JsonApiErrorException;
import io.xlate.jsonapi.rvp.internal.rs.boundary.ResourceObjectReader;

@SuppressWarnings("java:S1452") // Suppress Sonar warnings regarding generic wildcards
public class EntityMeta {

    private static final Logger LOGGER = Logger.getLogger(EntityMeta.class.getName());

    private static final Map<Class<?>, Class<?>> wrapperMap = Map.of(boolean.class,
                                                                     Boolean.class,
                                                                     byte.class,
                                                                     Byte.class,
                                                                     char.class,
                                                                     Character.class,
                                                                     double.class,
                                                                     Double.class,
                                                                     float.class,
                                                                     Float.class,
                                                                     int.class,
                                                                     Integer.class,
                                                                     long.class,
                                                                     Long.class,
                                                                     short.class,
                                                                     Short.class,
                                                                     void.class,
                                                                     Void.class);

    @SuppressWarnings("unchecked")
    public static <T> Class<T> wrap(Class<T> clazz) {
        return clazz.isPrimitive() ? (Class<T>) wrapperMap.get(clazz) : clazz;
    }

    private final JsonApiResourceType<?> configuredType;
    private final Class<?> resourceClass;
    private final BeanInfo beanInfo;
    private final Map<String, PropertyDescriptor> propertyDescriptors;

    private final EntityType<?> entityType;
    private final Set<String> methodsAllowed;

    private final Map<String, SingularAttribute<?, ?>> attributes;
    private final Map<String, Function<String, ? extends Object>> readers;

    private final Map<String, Attribute<?, ?>> relationships;

    public EntityMeta(Class<?> resourceClass,
            JsonApiResourceType<?> configuredType,
            Metamodel model,
            Set<Class<?>> knownTypes) {

        this.resourceClass = resourceClass;
        this.configuredType = configuredType;

        final Class<?> entityClass = configuredType.getResourceClass();

        try {
            this.beanInfo = Introspector.getBeanInfo(entityClass);
        } catch (IntrospectionException e) {
            throw new IllegalStateException("Failed to obtain BeanInfo for class: " + entityClass, e);
        }

        this.entityType = model.entity(entityClass);
        this.methodsAllowed = configuredType.getMethods()
                                            .stream().map(method -> method.getAnnotation(HttpMethod.class).value())
                                            .collect(Collectors.toSet());

        this.attributes = entityType.getSingularAttributes()
                                    .stream()
                                    .filter(a -> !a.isId()
                                            && !a.getName().equals(configuredType.getExposedIdAttribute())
                                            && !a.isAssociation()
                                            && a.getPersistentAttributeType() == PersistentAttributeType.BASIC)
                                    .collect(Collectors.toMap(Attribute::getName, Function.identity()));

        this.readers = attributes.values().stream()
                                 .filter(EntityMeta::readerRequired)
                                 .map(this::readerEntry)
                                 .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        this.relationships = entityType.getAttributes()
                                       .stream()
                                       .filter(Attribute::isAssociation)
                                       .filter(a -> this.configuredType.getRelationships().isEmpty()
                                               || this.configuredType.getRelationships().contains(a.getName()))
                                       .filter(a -> knownTypes.contains(Bindable.class.cast(a).getBindableJavaType()))
                                       .collect(Collectors.toMap(Attribute::getName, Function.identity()));

        this.propertyDescriptors = Arrays.stream(beanInfo.getPropertyDescriptors())
                                         .collect(Collectors.toMap(PropertyDescriptor::getName,
                                                                   descriptor -> descriptor));
    }

    static boolean readerRequired(SingularAttribute<?, ?> attribute) {
        Class<?> propertyType = attribute.getBindableJavaType();

        return !(Boolean.class.isAssignableFrom(propertyType)
                || Boolean.TYPE.equals(propertyType)
                || Number.class.isAssignableFrom(propertyType)
                || Character.class.isAssignableFrom(propertyType)
                || ResourceObjectReader.NUMBER_PRIMITIVES.contains(propertyType));
    }

    Map.Entry<String, Function<String, Object>> readerEntry(SingularAttribute<?, ?> attribute) {
        String name = attribute.getName();

        if (configuredType.getReaders().containsKey(name)) {
            return Map.entry(name, configuredType.getReaders().get(name));
        }

        Class<?> propertyType = attribute.getBindableJavaType();

        if (String.class.equals(propertyType)) {
            return Map.entry(name, Object.class::cast);
        }

        var parsers = Stream.concat(Arrays.stream(propertyType.getDeclaredConstructors()),
                                    Arrays.stream(propertyType.getDeclaredMethods()))
                            .filter(method -> Modifier.isStatic(method.getModifiers()))
                            .filter(method -> Objects.equals(method.getParameterCount(), 1))
                            .filter(this::parsingMethod)
                            .collect(Collectors.toMap(Executable::getName, method -> method));

        var parser = objectParserMethod(parsers, propertyType);

        return Map.entry(name, parser);
    }

    boolean parsingMethod(Executable method) {
        return fromCharSequence(method) || fromInstant(method);
    }

    boolean fromCharSequence(Executable method) {
        Class<?> paramType = method.getParameterTypes()[0];
        return paramType == String.class || paramType == CharSequence.class;
    }

    boolean fromInstant(Executable method) {
        return method.getParameterTypes()[0] == Instant.class && "from".equals(method.getName());
    }

    Function<String, Object> objectParserMethod(Map<String, Executable> methods, Class<?> propertyType) {
        if (propertyType.isAssignableFrom(OffsetDateTime.class)) {
            return OffsetDateTime::parse;
        }

        if (methods.containsKey("from") && methods.get("from").getParameterTypes()[0].equals(Instant.class)) {
            return value -> safeParse(value, raw -> {
                Instant instant = OffsetDateTime.parse(value).toInstant();
                return ((Method) methods.get("from")).invoke(null, instant);
            });
        }

        if (methods.containsKey("valueOf")) {
            return value -> safeParse(value, raw -> ((Method) methods.get("valueOf")).invoke(null, value));
        }

        if (methods.containsKey("parse")) {
            return value -> safeParse(value, raw -> ((Method) methods.get("parse")).invoke(null, value));
        }

        if (methods.containsKey("<init>")) {
            return value -> safeParse(value, raw -> Constructor.class.cast(methods.get("<init>")).newInstance(value));
        }

        return value -> null;
    }

    Object safeParse(String value, StringParser parser) {
        try {
            return parser.parse(value);
        } catch (Exception e) {
            LOGGER.finer(() -> "Error parsing string attribute: " + e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    interface StringParser {
        Object parse(String value) throws Exception; //NOSONAR - Not in control of thrown exceptions
    }

    public boolean isMethodAllowed(String method) {
        return this.methodsAllowed.contains(method);
    }

    public boolean isField(String name) {
        return getAttributes().containsKey(name) || getRelationships().containsKey(name);
    }

    @SuppressWarnings("unchecked")
    public SingularAttribute<Object, ?> getExposedIdAttribute() {
        final SingularAttribute<?, ?> attr;
        final String attributeName = configuredType.getExposedIdAttribute();

        if (attributeName != null) {
            attr = entityType.getSingularAttribute(attributeName);
        } else {
            attr = getIdAttribute();
        }

        return (SingularAttribute<Object, ?>) attr;
    }

    public Map<String, SingularAttribute<?, ?>> getAttributes() {
        return attributes;
    }

    public Set<String> getAttributeNames() {
        return attributes.keySet();
    }

    public Map<String, Function<String, ? extends Object>> getReaders() {
        return readers;
    }

    public Map<String, Attribute<?, ?>> getRelationships() {
        return relationships;
    }

    public Set<String> getRelationshipNames() {
        return relationships.keySet();
    }

    public Object readId(String value) {
        return configuredType.getIdReader().apply(value);
    }

    @SuppressWarnings("unchecked")
    public SingularAttribute<Object, ?> getIdAttribute() {
        Class<?> type = entityType.getIdType().getJavaType();
        return (SingularAttribute<Object, ?>) entityType.getId(type);
    }

    public Object getIdValue(Object bean) {
        return getPropertyValue(bean, getIdAttribute().getName());
    }

    public Object getExposedIdValue(Object bean) {
        return getPropertyValue(bean, getExposedIdAttribute().getName());
    }

    public Class<?> getResourceClass() {
        return resourceClass;
    }

    @SuppressWarnings("unchecked")
    public Class<Object> getEntityClass() {
        return (Class<Object>) configuredType.getResourceClass();
    }

    public String getResourceType() {
        return configuredType.getName();
    }

    @SuppressWarnings("unchecked")
    public EntityType<Object> getEntityType() {
        return (EntityType<Object>) entityType;
    }

    public boolean hasAttribute(String attributeName) {
        return getAttributes().containsKey(attributeName);
    }

    public boolean isRelatedTo(String relationshipName) {
        return getRelationships().containsKey(relationshipName);
    }

    @SuppressWarnings("unchecked")
    public Class<Object> getRelatedEntityClass(String relationshipName) {
        if (isRelatedTo(relationshipName)) {
            Attribute<?, ?> attr = entityType.getAttribute(relationshipName);
            if (attr.isCollection()) {
                return (Class<Object>) ((PluralAttribute<?, ?, ?>) attr).getBindableJavaType();
            }
            return (Class<Object>) attr.getJavaType();
        }

        return null;
    }

    public String getPrincipalNamePath() {
        return configuredType.getPrincipalNamePath();
    }

    public PropertyDescriptor getPropertyDescriptor(String name) {
        PropertyDescriptor descriptor = propertyDescriptors.get(name);

        if (descriptor != null) {
            return descriptor;
        }

        throw new NoSuchElementException(name);
    }

    @SuppressWarnings("unchecked")
    public <T> T getPropertyValue(Object bean, String name) {
        PropertyDescriptor descriptor = getPropertyDescriptor(name);

        try {
            return (T) descriptor.getReadMethod().invoke(bean);
        } catch (Exception e) {
            throw new JsonApiErrorException(Status.INTERNAL_SERVER_ERROR, "Server Error", "Unable to read property");
        }
    }

    public <T> void setPropertyValue(Object bean, String name, T value) {
        PropertyDescriptor descriptor = getPropertyDescriptor(name);

        try {
            descriptor.getWriteMethod().invoke(bean, value);
        } catch (Exception e) {
            throw new JsonApiErrorException(Status.INTERNAL_SERVER_ERROR, "Server Error", "Unable to update property");
        }
    }

    public Set<String> getUniqueTuple(String name) {
        return configuredType.getUniqueTuples().get(name);
    }
}

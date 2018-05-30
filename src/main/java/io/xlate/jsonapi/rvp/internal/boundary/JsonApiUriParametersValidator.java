/*******************************************************************************
 * Copyright (C) 2018 xlate.io LLC, http://www.xlate.io
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 * 
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/
package io.xlate.jsonapi.rvp.internal.boundary;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.persistence.metamodel.Attribute;
import javax.persistence.metamodel.EntityType;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.ws.rs.core.MultivaluedMap;

import io.xlate.jsonapi.rvp.internal.entity.FetchParameters;

public class JsonApiUriParametersValidator
        implements ConstraintValidator<ValidJsonApiUriParameters, FetchParameters> {

    @SuppressWarnings("unused")
    private ValidJsonApiUriParameters annotation;

    @Override
    public void initialize(ValidJsonApiUriParameters constraintAnnotation) {
        this.annotation = constraintAnnotation;
    }

    @Override
    public boolean isValid(FetchParameters value, ConstraintValidatorContext context) {
        boolean valid = true;

        MultivaluedMap<String, String> params = value.getUriInfo().getQueryParameters();
        String resourceType = value.getEntityMeta().getResourceType();
        EntityType<Object> rootType = value.getEntityMeta().getEntityType();
        String id = value.getId();

        if (params.containsKey(FetchParameters.PARAM_INCLUDE)) {
            List<String> includeParams = params.get(FetchParameters.PARAM_INCLUDE);
            valid = validateSingle(FetchParameters.PARAM_INCLUDE, includeParams, context, valid);

            String includeParam = includeParams.get(0);
            Set<String> includes = new HashSet<>();
            Map<String, List<String>> fields = new HashMap<>();

            for (String include : includeParam.split(",")) {
                String attribute = ResourceObjectReader.toAttributeName(include);

                if (includes.contains(attribute)) {
                    valid = false;
                    context.buildConstraintViolationWithTemplate(""
                            + "The relationshop path `" + include + "` is listed multiple times.")
                           .addPropertyNode(FetchParameters.PARAM_INCLUDE)
                           .addConstraintViolation();
                } else {
                    try {
                        Attribute<?, ?> attr = rootType.getAttribute(attribute);

                        if (!attr.isAssociation()) {
                            valid = false;
                            context.buildConstraintViolationWithTemplate(""
                                    + "Attribute `" + include + "` is not a relationship.")
                                   .addPropertyNode(FetchParameters.PARAM_INCLUDE)
                                   .addConstraintViolation();
                        } else {
                            if (!FetchParameters.includeField(fields, resourceType, attribute)) {
                                valid = false;
                                context.buildConstraintViolationWithTemplate(""
                                        + "Cannot include relationshop `" + include
                                        + "` not selected by parameter `field[" + resourceType + "]`.")
                                       .addPropertyNode(FetchParameters.PARAM_INCLUDE)
                                       .addConstraintViolation();
                            }
                        }
                    } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
                        valid = false;
                        context.buildConstraintViolationWithTemplate(""
                                + "The resource does not have a `" + include + "` relationship path.")
                               .addPropertyNode(FetchParameters.PARAM_INCLUDE)
                               .addConstraintViolation();
                    }

                    FetchParameters.addField(fields, resourceType, attribute);
                }
            }
        }

        if (params.containsKey(FetchParameters.PARAM_SORT)) {
            if (id != null) {
                valid = false;
                context.buildConstraintViolationWithTemplate(""
                        + "Cannot sort a single resource, `" + id + "`")
                       .addPropertyNode(FetchParameters.PARAM_SORT)
                       .addConstraintViolation();
            } else {
                List<String> sortParams = params.get(FetchParameters.PARAM_SORT);
                valid = validateSingle(FetchParameters.PARAM_SORT, sortParams, context, valid);

                String sortParam = sortParams.get(0);

                for (String sort : sortParam.split(",")) {
                    boolean descending = sort.startsWith("-");
                    String attribute = ResourceObjectReader.toAttributeName(sort.substring(descending ? 1 : 0));

                    try {
                        Attribute<?, ?> attr = rootType.getAttribute(attribute);

                        if (attr.isAssociation()) {
                            valid = false;
                            context.buildConstraintViolationWithTemplate(""
                                    + "Sort key `" + sort + "` is not an attribute.")
                                   .addPropertyNode(FetchParameters.PARAM_SORT)
                                   .addConstraintViolation();
                        }
                    } catch (@SuppressWarnings("unused") IllegalArgumentException e) {
                        valid = false;
                        context.buildConstraintViolationWithTemplate(""
                                + "The resource does not have a `" + sort + "` attribute.")
                               .addPropertyNode(FetchParameters.PARAM_SORT)
                               .addConstraintViolation();
                    }
                }
            }
        }

        valid = validatePaging(id, FetchParameters.PARAM_PAGE_OFFSET, params, context, valid);
        valid = validatePaging(id, FetchParameters.PARAM_PAGE_LIMIT, params, context, valid);

        return valid;
    }

    boolean validatePaging(String id,
                           String paramName,
                           MultivaluedMap<String, String> params,
                           ConstraintValidatorContext context,
                           boolean valid) {

        if (params.containsKey(paramName)) {
            if (id != null) {
                valid = false;
                context.buildConstraintViolationWithTemplate(""
                        + "Pagination invalid for single resource requests.")
                       .addPropertyNode(paramName)
                       .addConstraintViolation();
            } else {
                List<String> pageParamValues = params.get(paramName);
                valid = validateSingle(paramName, pageParamValues, context, valid);

                try {
                    Integer.parseInt(pageParamValues.get(0));
                } catch (@SuppressWarnings("unused") NumberFormatException e) {
                    valid = false;
                    context.buildConstraintViolationWithTemplate(""
                            + "Paging parameter must be an integer.")
                           .addPropertyNode(paramName)
                           .addConstraintViolation();
                }
            }
        }
        return valid;
    }

    boolean validateSingle(String paramName,
                           List<String> paramValues,
                           ConstraintValidatorContext context,
                           boolean valid) {

        if (paramValues.size() > 1) {
            valid = false;
            context.buildConstraintViolationWithTemplate("Multiple `" + paramName + "` parameters are not supported.")
                   .addPropertyNode(paramName)
                   .addConstraintViolation();
        }
        return valid;
    }
}
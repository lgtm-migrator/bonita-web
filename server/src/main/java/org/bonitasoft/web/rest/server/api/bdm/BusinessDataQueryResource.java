/** Copyright (C) 2014 Bonitasoft S.A.
 * BonitaSoft, 32 rue Gustave Eiffel - 38000 Grenoble
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2.0 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.bonitasoft.web.rest.server.api.bdm;

import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import org.bonitasoft.engine.api.CommandAPI;
import org.bonitasoft.engine.bpm.businessdata.BusinessDataQueryMetadata;
import org.bonitasoft.engine.bpm.businessdata.BusinessDataQueryResult;
import org.bonitasoft.engine.command.CommandExecutionException;
import org.bonitasoft.engine.command.CommandNotFoundException;
import org.bonitasoft.engine.command.CommandParameterizationException;
import org.bonitasoft.web.rest.server.api.resource.CommonResource;
import org.restlet.data.CharacterSet;
import org.restlet.data.MediaType;
import org.restlet.representation.Representation;
import org.restlet.resource.Get;

/**
 * @author Laurent Leseigneur
 */
public class BusinessDataQueryResource extends CommonResource {

    public static final String COMMAND_NAME = "getBusinessDataByQueryCommand";

    private final CommandAPI commandAPI;

    /**
     * Logger
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(BusinessDataQueryResource.class.getName());
    
    public BusinessDataQueryResource(final CommandAPI commandAPI) {
        this.commandAPI = commandAPI;
        //Prevent Restlet from setting the status to 404
        //HTTP conditional headers are not supported when setting the entity manually in the response (fix BS-18149)
        setConditional(false);
    }

    @Get("json")
    public void getProcessBusinessDataQuery() throws CommandNotFoundException, CommandParameterizationException, CommandExecutionException, IOException {
        final Map<String, Serializable> parameters = new HashMap<>();
        final Integer searchPageNumber = getSearchPageNumber();
        final Integer searchPageSize = getSearchPageSize();

        parameters.put("queryName", getQueryParameter(true));
        parameters.put("queryParameters", (Serializable) getSearchFilters());
        parameters.put("entityClassName", getPathParam("className"));
        parameters.put("startIndex", searchPageNumber * searchPageSize);
        parameters.put("maxResults", searchPageSize);
        parameters.put("businessDataURIPattern", BusinessDataFieldValue.URI_PATTERN);

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Executing business Data Query: " + parameters.get("queryName"));
            LOGGER.debug("entityClassName: " + parameters.get("entityClassName"));
            LOGGER.debug("queryParameters: " + parameters.get("queryParameters").toString());
            LOGGER.debug("startIndex: " + parameters.get("startIndex"));
            LOGGER.debug("maxResults: " + parameters.get("maxResults"));
        }
        
        BusinessDataQueryResult businessDataQueryResult = (BusinessDataQueryResult) commandAPI.execute(COMMAND_NAME, parameters);

        Representation representation = getConverterService().toRepresentation(businessDataQueryResult.getJsonResults(), MediaType.APPLICATION_JSON);
        representation.setCharacterSet(CharacterSet.UTF_8);
        getResponse().setEntity(representation);
        final BusinessDataQueryMetadata businessDataQueryMetadata = businessDataQueryResult.getBusinessDataQueryMetadata();
        if (businessDataQueryMetadata != null) {
            setContentRange(searchPageNumber, searchPageSize, businessDataQueryMetadata.getCount());
        }
    }

}

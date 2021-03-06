/*
 * #%L
 * Alfresco Sharepoint Protocol
 * %%
 * Copyright (C) 2005 - 2016 Alfresco Software Limited
 * %%
 * This file is part of the Alfresco software. 
 * If the software was purchased under a paid Alfresco license, the terms of 
 * the paid license agreement will prevail.  Otherwise, the software is 
 * provided under the following open source license terms:
 * 
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */
package org.alfresco.module.vti.web.ws;

import java.util.Date;
import java.util.HashMap;

import org.alfresco.module.vti.handler.ListServiceHandler;
import org.alfresco.module.vti.handler.MethodHandler;
import org.alfresco.module.vti.metadata.model.DocsMetaInfo;
import org.alfresco.module.vti.metadata.model.ListInfoBean;

/**
 * Class for handling GetListItemChangesSinceToken method from lists web service
 *
 * @author PavelYur
 */
public class GetListItemChangesSinceTokenEndpoint extends GetListItemsEndpoint
{
    /**
     * Constructor
     */
    public GetListItemChangesSinceTokenEndpoint(ListServiceHandler listHander, MethodHandler methodHandler)
    {
        super(listHander, methodHandler);
    }

    /**
     * TODO Support all kinds of lists
     * TODO Filter by change token
     */
    @Override
    protected DocsMetaInfo getListInfo(String siteName, ListInfoBean list, String initialUrl, Date changesSince)
    {
        return methodHandler.getListDocuments(siteName, false, false, "", initialUrl, false, false, true, true, false, false, false, false, new HashMap<String, Object>(0), false);
    }
}
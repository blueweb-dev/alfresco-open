/*
 * #%L
 * Alfresco Solr 4
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
package org.alfresco.solr;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.alfresco.encryption.KeyResourceLoader;
import org.apache.solr.core.SolrResourceLoader;

/**
 * Loads encryption key resources from a Solr core installation using a SolrResourceLoader.
 * 
 * @since 4.0
 */
public class SolrKeyResourceLoader implements KeyResourceLoader
{
	private SolrResourceLoader loader;

	public SolrKeyResourceLoader(SolrResourceLoader loader)
	{
		this.loader = loader;
	}

	@Override
	public InputStream getKeyStore(String location)
			throws FileNotFoundException
	{
		try
        {
            return loader.openResource(location);
        }
        catch (IOException e)
        {
            // TODO: SOLR API changes mean that IOException must be handled.
            // This may need revisiting.
            throw new FileNotFoundException("Caused by " + e.getMessage());
        }
	}

	@Override
	public Properties loadKeyMetaData(String location) throws IOException
	{
		Properties p = new Properties();
		InputStream stream = loader.openResource(location);
		p.load(stream);
		stream.close();
		return p;
	}
}

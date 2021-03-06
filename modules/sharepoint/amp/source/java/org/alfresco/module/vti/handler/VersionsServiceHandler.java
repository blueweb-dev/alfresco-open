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
package org.alfresco.module.vti.handler;

import java.util.List;

import org.alfresco.module.vti.metadata.model.DocumentVersionBean;
import org.alfresco.service.cmr.model.FileNotFoundException;
import org.alfresco.service.cmr.version.VersionDoesNotExistException;

/**
 * Interface for the Versions web service handler.
 * 
 * Note that Versioning in SharePoint and Alfresco will sometimes have
 *  different semantics. 
 *  
 * This interface works with SharePoint versioning, and it's up to the 
 * implementation to translate (where needed) to Alfresco style versioning. 
 * 
 * @author Dmitry Lazurkin
 */
public interface VersionsServiceHandler
{
    /**
     * Returns information about the versions of the specified file
     * 
     * @param fileName A string that contains the site-relative URL of the file in the form Folder_Name/File_Name
     * @return information about the versions of the specified file
     */
    public List<DocumentVersionBean> getVersions(String fileName) throws FileNotFoundException;

    /**
     * Restores the specified file version
     * 
     * @param fileName site relative url to the file
     * @param fileVersion file version to restore
     * @return list of DocumentVersion beans
     */
    public List<DocumentVersionBean> restoreVersion(String fileName, String fileVersion);

    /**
     * Deletes the specified file version
     * 
     * @param fileName site relative url to the file
     * @param fileVersion file version to restore
     * @return list of DocumentVersion beans
     */
    public List<DocumentVersionBean> deleteVersion(String fileName, String fileVersion) 
       throws FileNotFoundException, VersionDoesNotExistException;

    /**
     * Deletes "all versions" of the specified file.
     * 
     * This doesn't actually mean deleting the whole version history
     * though, it means deleting all except the last "Published Version"
     * (last major version), and the current version (if different).
     * 
     * @param fileName
     * @return DocumentVersionBean current document
     */
    public List<DocumentVersionBean> deleteAllVersions(String fileName) throws FileNotFoundException;
    
    
    /**
     * Is the specified file name a versioned file?
     * 
     * @param fileName
     * @return true if versionable, false otherwise.
     */
    public boolean isVersionable(String fileName);
    
    
    public String makeCurrentVersionURL(String host, String context, String dws, String fileName);
    
    public String makeVersionURL(String host, String context, String dws, DocumentVersionBean version);
    
    public String makeDocumentDetailsURL(String host, String context, String dws, String fileName);    
}

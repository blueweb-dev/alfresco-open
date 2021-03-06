/*
 * #%L
 * Alfresco Repository
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
package org.alfresco.repo.content.transform;

import java.io.File;
import java.io.IOException;

import org.alfresco.repo.content.MimetypeMap;
import org.alfresco.repo.content.filestore.FileContentReader;
import org.alfresco.repo.content.filestore.FileContentWriter;
import org.alfresco.repo.management.subsystems.ChildApplicationContextFactory;
import org.alfresco.repo.security.authentication.AuthenticationUtil;
import org.alfresco.service.cmr.repository.ContentReader;
import org.alfresco.service.cmr.repository.ContentWriter;
import org.alfresco.service.cmr.repository.TransformationOptions;
import org.alfresco.util.TempFileProvider;

/**
 * Test class for ArchiveContentTransformer.
 * 
 * @see org.alfresco.repo.content.transform.ArchiveContentTransformer
 * 
 * @author Neil McErlean
 */
public class ArchiveContentTransformerTest extends AbstractContentTransformerTest
{
    private ArchiveContentTransformer transformer;

    private ContentTransformerRegistry registry;
    
    @Override
    public void setUp() throws Exception
    {
        super.setUp();
        
        transformer = new ArchiveContentTransformer();
        transformer.setMimetypeService(mimetypeService);
        transformer.setTransformerDebug(transformerDebug);
        transformer.setTransformerConfig(transformerConfig);

        registry = (ContentTransformerRegistry) ctx.getBean("contentTransformerRegistry");
    }

    protected ContentTransformer getTransformer(String sourceMimetype, String targetMimetype)
    {
        return transformer;
    }
    
    public void testIsTransformable() throws Exception
    {
        assertTrue(transformer.isTransformable(MimetypeMap.MIMETYPE_ZIP, -1, MimetypeMap.MIMETYPE_TEXT_PLAIN, new TransformationOptions()));
        assertTrue(transformer.isTransformable("application/x-tar", -1, MimetypeMap.MIMETYPE_TEXT_PLAIN, new TransformationOptions()));

        // TODO should this work ?
        //assertTrue(transformer.isTransformable("application/x-gtar", -1, MimetypeMap.MIMETYPE_TEXT_PLAIN, new TransformationOptions()));
    }

    @Override
    protected boolean isQuickPhraseExpected(String targetMimetype)
    {
       // The Zip transformer produces names of the entries, not their contents.
       return false;
    }

    @Override
    protected boolean isQuickWordsExpected(String targetMimetype)
    {
       // The Zip transformer produces names of the entries, not their contents.
       return false;
    }
    
    public void testRecursing() throws Exception
    {
       ContentWriter writer;
       String contents;
       
       // Bean off, no options
       transformer.setIncludeContents("FALSE");
       
       writer = getTestWriter();
       transformer.transform(getTestReader(), writer);
       contents = writer.getReader().getContentString();
       testHasFiles(contents);
       testNested(contents, false);
       
       
       // Bean on, no options
       transformer.setIncludeContents("TRUE");

       writer = getTestWriter();
       transformer.transform(getTestReader(), writer);
       contents = writer.getReader().getContentString();
       testHasFiles(contents);
       testNested(contents, true);


       // Bean off, Transformation Options off
       TransformationOptions options = new TransformationOptions();
       transformer.setIncludeContents("FALSE");
       
       writer = getTestWriter();
       transformer.transform(getTestReader(), writer, options);
       contents = writer.getReader().getContentString();
       testHasFiles(contents);
       testNested(contents, false);

       
       // Bean on, Transformation Options off
       transformer.setIncludeContents("T");

       writer = getTestWriter();
       transformer.transform(getTestReader(), writer, options);
       contents = writer.getReader().getContentString();
       testHasFiles(contents);
       testNested(contents, true);
       
       
       // Bean off, Transformation Options on - options win
       options.setIncludeEmbedded(true);
       transformer.setIncludeContents("FALSE");
       
       writer = getTestWriter();
       transformer.transform(getTestReader(), writer, options);
       contents = writer.getReader().getContentString();
       testHasFiles(contents);
       testNested(contents, true);
       
       
       // Bean on, Transformation Options on
       transformer.setIncludeContents("YeS");

       writer = getTestWriter();
       transformer.transform(getTestReader(), writer, options);
       contents = writer.getReader().getContentString();
       testHasFiles(contents);
       testNested(contents, true);
    }
    private ContentReader getTestReader() throws IOException {
       ContentReader sourceReader = new FileContentReader(
             loadQuickTestFile("zip")
       );
       sourceReader.setMimetype(MimetypeMap.MIMETYPE_ZIP);
       return sourceReader;
    }
    private ContentWriter getTestWriter() throws IOException {
       ContentWriter writer = new FileContentWriter(TempFileProvider.createTempFile("test", ".txt"));
       writer.setMimetype(MimetypeMap.MIMETYPE_TEXT_PLAIN);
       return writer;
    }
    private void testHasFiles(String contents) 
    {
       assertTrue("Files not found in " + contents,
             contents.contains("quick.txt"));
       assertTrue("Files not found in " + contents,
             contents.contains("quick.doc"));
       assertTrue("Files not found in " + contents,
             contents.contains("subfolder/quick.jpg"));
    }
    private void testNested(String contents, boolean shouldHaveRecursed)
    {
       assertEquals(
             "Recursion was " + shouldHaveRecursed + 
             " but content was " + contents,
             shouldHaveRecursed,
             contents.contains("The quick brown fox jumps over the lazy dog")
       );
       assertEquals(
             "Recursion was " + shouldHaveRecursed + 
             " but content was " + contents,
             shouldHaveRecursed,
             contents.contains("Le renard brun rapide saute par-dessus le chien paresseux")
       );
    }

    public void testArchiveToPdf() throws Exception
    {
        String sourceMimetype = MimetypeMap.MIMETYPE_ZIP;
        String targetMimetype = MimetypeMap.MIMETYPE_PDF;

        // force Transformers subsystem to start (this will also load the ContentTransformerRegistry - including complex/dynamic pipelines)
        // note: a call to contentService.getTransformer would also do this .. even if transformer cannot be found (returned as null)
        ChildApplicationContextFactory transformersSubsystem = (ChildApplicationContextFactory) ctx.getBean("Transformers");
        transformersSubsystem.start();

        assertNotNull(registry.getTransformer("transformer.complex.ArchiveToPdf"));

        // note: txt -> pdf currently uses OpenOffice/LibreOffice
        if (! isOpenOfficeWorkerAvailable())
        {
            // no connection
            System.err.println("ooWorker not available - skipping testArchiveToPdf !!");
            return;
        }

        AuthenticationUtil.setFullyAuthenticatedUser(AuthenticationUtil.getSystemUserName());
        ContentTransformer transformer = serviceRegistry.getContentService().getTransformer(sourceMimetype, targetMimetype);
        assertNotNull(transformer);

        String sourceExtension = mimetypeService.getExtension(sourceMimetype);
        String targetExtension = mimetypeService.getExtension(targetMimetype);

        File zipSourceFile = loadQuickTestFile("zip");
        ContentReader sourceReader = new FileContentReader(zipSourceFile);

        // make a writer for the target file
        File targetFile = TempFileProvider.createTempFile(getClass().getSimpleName() + "_"
                + getName() + "_" + sourceExtension + "_", "." + targetExtension);
        ContentWriter targetWriter = new FileContentWriter(targetFile);

        // do the transformation
        sourceReader.setMimetype(sourceMimetype);
        targetWriter.setMimetype(targetMimetype);
        transformer.transform(sourceReader.getReader(), targetWriter);

        ContentReader targetReader = new FileContentReader(targetFile);
        assertTrue(targetReader.getSize() > 0);
    }
}

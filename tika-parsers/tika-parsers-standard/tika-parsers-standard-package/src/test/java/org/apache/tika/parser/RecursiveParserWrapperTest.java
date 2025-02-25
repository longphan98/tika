/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.tika.parser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.ClosedInputStream;
import org.apache.commons.io.input.ProxyInputStream;
import org.junit.jupiter.api.Test;

import org.apache.tika.TikaTest;
import org.apache.tika.config.TikaConfig;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.metadata.TikaCoreProperties;
import org.apache.tika.parser.digestutils.CommonsDigester;
import org.apache.tika.sax.AbstractRecursiveParserWrapperHandler;
import org.apache.tika.sax.BasicContentHandlerFactory;
import org.apache.tika.sax.ContentHandlerFactory;
import org.apache.tika.sax.RecursiveParserWrapperHandler;

public class RecursiveParserWrapperTest extends TikaTest {

    @Test
    public void testBasicXML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.contains("<p class=\"header\" />"));
    }

    @Test
    public void testBasicHTML() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.HTML, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        //not much differentiates html from xml in this test file
        assertTrue(content.contains("<p class=\"header\"></p>"));
    }

    @Test
    public void testBasicText() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        assertFalse(content.contains("<p "));
        assertTrue(content.contains("embed_0"));
    }

    @Test
    public void testIgnoreContent() throws Exception {
        List<Metadata> list = getMetadata(new Metadata(),
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.IGNORE, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        assertNull(content);
    }


    @Test
    public void testCharLimit() throws Exception {
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);
        InputStream stream = getResourceAsStream("/test-documents/test_recursive_embedded.docx");
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, 70));
        wrapper.parse(stream, handler, metadata, context);
        List<Metadata> list = handler.getMetadataList();

        assertEquals(5, list.size());

        int wlr = 0;
        for (Metadata m : list) {
            String limitReached = m.get(TikaCoreProperties.WRITE_LIMIT_REACHED);
            if (limitReached != null && limitReached.equals("true")) {
                wlr++;
            }
        }
        assertEquals(2, wlr);
    }


    @Test
    public void testMaxEmbedded() throws Exception {
        int maxEmbedded = 4;
        int totalNoLimit = 12;//including outer container file
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        String limitReached = null;

        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER);

        InputStream stream = getResourceAsStream("/test-documents/test_recursive_embedded.docx");
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(

                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        wrapper.parse(stream, handler, metadata, context);
        List<Metadata> list = handler.getMetadataList();
        //test default
        assertEquals(totalNoLimit, list.size());

        limitReached = list.get(0)
                .get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertNull(limitReached);

        stream.close();

        //test setting value
        metadata = new Metadata();
        stream = getResourceAsStream("/test-documents/test_recursive_embedded.docx");
        handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                maxEmbedded);
        wrapper.parse(stream, handler, metadata, context);
        list = handler.getMetadataList();
        //add 1 for outer container file
        assertEquals(maxEmbedded + 1, list.size());

        limitReached = list.get(0)
                .get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertEquals("true", limitReached);

        stream.close();

        //test setting value < 0
        metadata = new Metadata();
        stream = getResourceAsStream("/test-documents/test_recursive_embedded.docx");
        handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                -2);
        wrapper.parse(stream, handler, metadata, context);
        list = handler.getMetadataList();
        assertEquals(totalNoLimit, list.size());
        limitReached = list.get(0)
                .get(AbstractRecursiveParserWrapperHandler.EMBEDDED_RESOURCE_LIMIT_REACHED);
        assertNull(limitReached);
    }


    @Test
    public void testEmbeddedResourcePath() throws Exception {

        Set<String> targets = new HashSet<>();
        targets.add("/embed1.zip");
        targets.add("/embed1.zip/embed2.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed4.zip");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed4.zip/embed4.txt");
        targets.add("/embed1.zip/embed2.zip/embed3.zip/embed3.txt");
        targets.add("/embed1.zip/embed2.zip/embed2a.txt");
        targets.add("/embed1.zip/embed2.zip/embed2b.txt");
        targets.add("/embed1.zip/embed1b.txt");
        targets.add("/embed1.zip/embed1a.txt");
        targets.add("/image1.emf");

        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.XML, -1));
        Metadata container = list.get(0);
        String content = container.get(TikaCoreProperties.TIKA_CONTENT);
        assertTrue(content.contains("<p class=\"header\" />"));

        Set<String> seen = new HashSet<>();
        for (Metadata m : list) {
            String path = m.get(TikaCoreProperties.EMBEDDED_RESOURCE_PATH);
            if (path != null) {
                seen.add(path);
            }
        }
        assertEquals(targets, seen);
    }

    @Test
    public void testEmbeddedNPE() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded_npe.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));
        //default behavior (user doesn't specify whether or not to catch embedded exceptions
        //is to catch the exception
        assertEquals(13, list.size());
        Metadata mockNPEMetadata = list.get(10);
        assertContains("java.lang.NullPointerException",
                mockNPEMetadata.get(TikaCoreProperties.EMBEDDED_EXCEPTION));

        metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded_npe.docx");
        list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                false, null);

        //Composite parser swallows caught TikaExceptions, IOExceptions and SAXExceptions
        //and just doesn't bother to report that there was an exception.
        assertEquals(13, list.size());
    }

    @Test
    public void testPrimaryExcWEmbedded() throws Exception {
        //if embedded content is handled and then
        //the parser hits an exception in the container document,
        //that the first element of the returned list is the container document
        //and the second is the embedded content
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "embedded_then_npe.xml");

        ParseContext context = new ParseContext();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER, true);
        RecursiveParserWrapperHandler handler = new RecursiveParserWrapperHandler(
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1));

        String path = "/test-documents/mock/embedded_then_npe.xml";

        InputStream stream = null;
        boolean npe = false;
        try {
            stream = getResourceAsStream(path);
            wrapper.parse(stream, handler, metadata, context);
        } catch (TikaException e) {
            if (e.getCause().getClass().equals(NullPointerException.class)) {
                npe = true;
            }
        } finally {
            IOUtils.closeQuietly(stream);
        }
        assertTrue(npe, "npe");

        List<Metadata> metadataList = handler.getMetadataList();
        assertEquals(2, metadataList.size());
        Metadata outerMetadata = metadataList.get(0);
        Metadata embeddedMetadata = metadataList.get(1);
        assertContains("main_content", outerMetadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("embedded_then_npe.xml",
                outerMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("Nikolai Lobachevsky", outerMetadata.get("author"));

        assertContains("some_embedded_content",
                embeddedMetadata.get(TikaCoreProperties.TIKA_CONTENT));
        assertEquals("embed1.xml", embeddedMetadata.get(TikaCoreProperties.RESOURCE_NAME_KEY));
        assertEquals("embeddedAuthor", embeddedMetadata.get("author"));
    }

    @Test
    public void testDigesters() throws Exception {
        Metadata metadata = new Metadata();
        metadata.set(TikaCoreProperties.RESOURCE_NAME_KEY, "test_recursive_embedded.docx");
        List<Metadata> list = getMetadata(metadata,
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1),
                true, new CommonsDigester(100000, "md5"));
        int i = 0;
        Metadata m0 = list.get(0);
        Metadata m6 = list.get(6);
        String md5Key = "X-TIKA:digest:MD5";
        assertEquals("59f626e09a8c16ab6dbc2800c685f772", list.get(0).get(md5Key));
        assertEquals("ccdf3882e7e4c2454e28884db9b0a54d", list.get(6).get(md5Key));
        assertEquals("a869bf6432ebd14e19fc79416274e0c9", list.get(7).get(md5Key));
    }

    @Test
    public void testStreamNotClosed() throws Exception {
        //TIKA-2974
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(AUTO_DETECT_PARSER, true);
        String path = "/test-documents/test_recursive_embedded.docx";
        ContentHandlerFactory contentHandlerFactory =
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1);

        CloseCountingInputStream stream = null;
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(contentHandlerFactory);
        try {
            stream = new CloseCountingInputStream(getResourceAsStream(path));
            wrapper.parse(stream, handler, metadata, context);
            assertEquals(0, stream.counter);
        } finally {
            IOUtils.closeQuietly(stream);
        }

    }

    @Test
    public void testIncludeFilter() throws Exception {
        //TIKA-3137
        ParseContext context = new ParseContext();
        Metadata metadata = new Metadata();
        TikaConfig tikaConfig = null;
        Parser p = null;
        try (InputStream is = getResourceAsStream("TIKA-3137-include.xml")) {
            tikaConfig = new TikaConfig(is);
            p = new AutoDetectParser(tikaConfig);
        }
        RecursiveParserWrapper wrapper = new RecursiveParserWrapper(p, true);
        String path = "/test-documents/test_recursive_embedded.docx";
        ContentHandlerFactory contentHandlerFactory =
                new BasicContentHandlerFactory(BasicContentHandlerFactory.HANDLER_TYPE.TEXT, -1);

        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(contentHandlerFactory, -1,
                        tikaConfig.getMetadataFilter());
        try (InputStream is = getResourceAsStream(path)) {
            wrapper.parse(is, handler, metadata, context);
        }
        List<Metadata> metadataList = handler.getMetadataList();
        assertEquals(5, metadataList.size());

        Set<String> expectedKeys = new HashSet<>();
        expectedKeys.add("X-TIKA:content");
        expectedKeys.add("extended-properties:Application");
        expectedKeys.add("Content-Type");
        for (Metadata m : metadataList) {
            if (m.get(Metadata.CONTENT_TYPE).equals("image/emf")) {
                fail("emf should have been filtered out");
            }
            if (m.get(Metadata.CONTENT_TYPE).startsWith("text/plain")) {
                fail("text/plain should have been filtered out");
            }
            assertTrue(m.names().length >= 2);
            for (String n : m.names()) {
                if (!expectedKeys.contains(n)) {
                    fail("didn't expect " + n);
                }
            }
        }
    }

    private List<Metadata> getMetadata(Metadata metadata,
                                       ContentHandlerFactory contentHandlerFactory,
                                       boolean catchEmbeddedExceptions,
                                       DigestingParser.Digester digester) throws Exception {
        ParseContext context = new ParseContext();
        Parser wrapped = AUTO_DETECT_PARSER;
        if (digester != null) {
            wrapped = new DigestingParser(wrapped, digester);
        }
        RecursiveParserWrapper wrapper =
                new RecursiveParserWrapper(wrapped, catchEmbeddedExceptions);
        String path = metadata.get(TikaCoreProperties.RESOURCE_NAME_KEY);
        if (path == null) {
            path = "/test-documents/test_recursive_embedded.docx";
        } else {
            path = "/test-documents/" + path;
        }
        InputStream stream = null;
        RecursiveParserWrapperHandler handler =
                new RecursiveParserWrapperHandler(contentHandlerFactory);
        try {
            stream = TikaInputStream.get(getResourceAsUri(path));
            wrapper.parse(stream, handler, metadata, context);
        } finally {
            IOUtils.closeQuietly(stream);
        }
        return handler.getMetadataList();
    }

    private List<Metadata> getMetadata(Metadata metadata,
                                       ContentHandlerFactory contentHandlerFactory)
            throws Exception {
        return getMetadata(metadata, contentHandlerFactory, true, null);
    }

    private static class CloseCountingInputStream extends ProxyInputStream {
        int counter = 0;

        public CloseCountingInputStream(InputStream in) {
            super(in);
        }

        /**
         * Replaces the underlying input stream with a {@link ClosedInputStream}
         * sentinel. The original input stream will remain open, but this proxy
         * will appear closed.
         */
        @Override
        public void close() throws IOException {
            in.close();
            counter++;
        }
    }
}

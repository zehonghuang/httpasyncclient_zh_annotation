/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package org.apache.http.nio.client.methods;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.concurrent.Future;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.io.output.FileWriterWithEncoding;
import org.apache.http.Consts;
import org.apache.http.localserver.HttpAsyncTestBase;
import org.apache.http.HttpEntity;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.BasicHttpEntity;
import org.apache.http.entity.ContentType;
import org.apache.http.nio.entity.NFileEntity;
import org.apache.http.nio.entity.NStringEntity;
import org.apache.http.nio.protocol.BasicAsyncRequestHandler;
import org.apache.http.protocol.HttpContext;
import org.apache.http.protocol.HttpRequestHandler;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class TestZeroCopy extends HttpAsyncTestBase {

    private static final String[] TEXT = {
        "blah blah blah blah blah blah blah blah blah blah blah blah blah blah",
        "yada yada yada yada yada yada yada yada yada yada yada yada yada yada",
        "da da da da da da da da da da da da da da da da da da da da da da da da",
        "nyet nyet nyet nyet nyet nyet nyet nyet nyet nyet nyet nyet nyet nyet"
    };

    private static final Charset ASCII = Charset.forName("ascii");
    private static File TEST_FILE;
    private File tmpfile;

    @BeforeClass
    public static void createSrcFile() throws Exception {
        final File tmpdir = FileUtils.getTempDirectory();
        TEST_FILE = new File(tmpdir, "src.test");
        final FileWriterWithEncoding out = new FileWriterWithEncoding(TEST_FILE, ASCII);
        try {
            for (int i = 0; i < 500; i++) {
                for (final String line: TEXT) {
                    out.write(line);
                    out.write("\r\n");
                }
            }
        } finally {
            out.close();
        }
    }

    @AfterClass
    public static void deleteSrcFile() throws Exception {
        if (TEST_FILE != null) {
            TEST_FILE.delete();
            TEST_FILE = null;
        }
    }

    @After
    public void cleanUp() throws Exception {
        if (this.tmpfile != null && this.tmpfile.exists()) {
            this.tmpfile.delete();
        }
    }

    static class TestZeroCopyPost extends BaseZeroCopyRequestProducer {

        private final boolean forceChunking;

        protected TestZeroCopyPost(
                final String requestURI,
                final boolean forceChunking) throws FileNotFoundException {
            super(URI.create(requestURI), TEST_FILE, ContentType.create("text/plain"));
            this.forceChunking = forceChunking;
        }

        @Override
        protected HttpEntityEnclosingRequest createRequest(final URI requestURI, final HttpEntity entity) {
            final HttpPost httppost = new HttpPost(requestURI);
            if (this.forceChunking) {
                final BasicHttpEntity chunkedEntity = new BasicHttpEntity();
                chunkedEntity.setChunked(true);
                httppost.setEntity(chunkedEntity);
            } else {
                httppost.setEntity(entity);
            }
            return httppost;
        }

    }

    static class TestZeroCopyConsumer extends ZeroCopyConsumer<Integer> {

        public TestZeroCopyConsumer(final File file) throws FileNotFoundException {
            super(file);
        }

        @Override
        protected Integer process(
                final HttpResponse response,
                final File file,
                final ContentType contentType) {
            return response.getStatusLine().getStatusCode();
        }

    }

    static class TestHandler implements HttpRequestHandler {

        private final boolean forceChunking;

        TestHandler(final boolean forceChunking) {
            super();
            this.forceChunking = forceChunking;
        }

        @Override
        public void handle(
                final HttpRequest request,
                final HttpResponse response,
                final HttpContext context) throws HttpException, IOException {
            HttpEntity requestEntity = null;
            if (request instanceof HttpEntityEnclosingRequest) {
                requestEntity = ((HttpEntityEnclosingRequest) request).getEntity();
            }
            if (requestEntity == null) {
                response.setEntity(new NStringEntity("Empty content"));
                return;
            }

            boolean ok = true;

            final InputStream instream = requestEntity.getContent();
            try {
                final ContentType contentType = ContentType.getOrDefault(requestEntity);
                Charset charset = contentType.getCharset();
                if (charset == null) {
                    charset = Consts.ISO_8859_1;
                }
                final LineIterator it = IOUtils.lineIterator(instream, charset.name());
                int count = 0;
                while (it.hasNext()) {
                    final String line = it.next();
                    final int i = count % TEXT.length;
                    final String expected = TEXT[i];
                    if (!line.equals(expected)) {
                        ok = false;
                        break;
                    }
                    count++;
                }
            } finally {
                instream.close();
            }
            if (ok) {
                final NFileEntity responseEntity = new NFileEntity(TEST_FILE,
                        ContentType.create("text/plian"));
                if (this.forceChunking) {
                    responseEntity.setChunked(true);
                }
                response.setEntity(responseEntity);
            } else {
                response.setEntity(new NStringEntity("Invalid content"));
            }
        }
    }

    @Test
    public void testTwoWayZeroCopy() throws Exception {
        this.serverBootstrap.registerHandler("*", new BasicAsyncRequestHandler(new TestHandler(false)));
        final HttpHost target = start();

        final File tmpdir = FileUtils.getTempDirectory();
        this.tmpfile = new File(tmpdir, "dst.test");
        final TestZeroCopyPost httppost = new TestZeroCopyPost(target.toURI() + "/bounce", false);
        final TestZeroCopyConsumer consumer = new TestZeroCopyConsumer(this.tmpfile);
        final Future<Integer> future = this.httpclient.execute(httppost, consumer, null);
        final Integer status = future.get();
        Assert.assertNotNull(status);
        Assert.assertEquals(HttpStatus.SC_OK, status.intValue());
        final InputStream instream = new FileInputStream(this.tmpfile);
        try {
            final LineIterator it = IOUtils.lineIterator(instream, ASCII.name());
            int count = 0;
            while (it.hasNext()) {
                final String line = it.next();
                final int i = count % TEXT.length;
                final String expected = TEXT[i];
                Assert.assertEquals(expected, line);
                count++;
            }
        } finally {
            instream.close();
        }
    }

    @Test
    public void testZeroCopyFallback() throws Exception {
        this.serverBootstrap.registerHandler("*", new BasicAsyncRequestHandler(new TestHandler(true)));
        final HttpHost target = start();
        final File tmpdir = FileUtils.getTempDirectory();
        this.tmpfile = new File(tmpdir, "dst.test");
        final TestZeroCopyPost httppost = new TestZeroCopyPost(target.toURI() + "/bounce", true);
        final TestZeroCopyConsumer consumer = new TestZeroCopyConsumer(this.tmpfile);
        final Future<Integer> future = this.httpclient.execute(httppost, consumer, null);
        final Integer status = future.get();
        Assert.assertNotNull(status);
        Assert.assertEquals(HttpStatus.SC_OK, status.intValue());
        final InputStream instream = new FileInputStream(this.tmpfile);
        try {
            final LineIterator it = IOUtils.lineIterator(instream, ASCII.name());
            int count = 0;
            while (it.hasNext()) {
                final String line = it.next();
                final int i = count % TEXT.length;
                final String expected = TEXT[i];
                Assert.assertEquals(expected, line);
                count++;
            }
        } finally {
            instream.close();
        }
    }

}

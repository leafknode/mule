/*
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */
package org.mule.test.module.http.functional.listener;

import static org.mule.service.http.api.HttpHeaders.Names.CONTENT_LENGTH;
import static org.mule.service.http.api.HttpHeaders.Names.TRANSFER_ENCODING;
import static org.mule.service.http.api.HttpHeaders.Values.CHUNKED;
import static org.mule.test.allure.AllureConstants.HttpFeature.HTTP_EXTENSION;
import static org.apache.http.client.fluent.Request.Get;
import static org.apache.http.client.fluent.Request.Post;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;

import org.mule.runtime.core.util.IOUtils;
import org.mule.tck.junit4.rule.DynamicPort;
import org.mule.tck.junit4.rule.SystemProperty;
import org.mule.test.module.http.functional.AbstractHttpTestCase;

import java.io.IOException;

import org.apache.commons.lang.RandomStringUtils;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.client.fluent.Response;
import org.junit.Rule;
import ru.yandex.qatools.allure.annotations.Features;

@Features(HTTP_EXTENSION)
public abstract class HttpListenerResponseStreamingTestCase extends AbstractHttpTestCase {

  private static final int DEFAULT_TIMEOUT = 1000;

  public static final String TEST_BODY = RandomStringUtils.randomAlphabetic(100 * 1024);
  public static final String TEST_BODY_MAP = "one=1&two=2";
  @Rule
  public SystemProperty stringPayloadLength = new SystemProperty("stringPayloadLength", String.valueOf(TEST_BODY.length()));
  @Rule
  public DynamicPort listenPort = new DynamicPort("port");

  protected abstract HttpVersion getHttpVersion();

  @Override
  protected String getConfigFile() {
    return "http-listener-response-streaming-config.xml";
  }

  protected String getUrl(String path) {
    return String.format("http://localhost:%s/%s", listenPort.getNumber(), path);
  }

  protected void testResponseIsContentLengthEncoding(String url, HttpVersion httpVersion) throws IOException {
    testResponseIsContentLengthEncoding(url, httpVersion, TEST_BODY);
  }

  protected void testResponseIsChunkedEncoding(String url, HttpVersion httpVersion) throws IOException {
    testResponseIsChunkedEncoding(url, httpVersion, TEST_BODY);
  }

  protected void testResponseIsNotChunkedEncoding(String url, HttpVersion httpVersion) throws IOException {
    testResponseIsNotChunkedEncoding(url, httpVersion, TEST_BODY);
  }

  protected void testResponseIsContentLengthEncoding(String url, HttpVersion httpVersion, String expectedBody)
      throws IOException {
    final Response response =
        Get(url).version(httpVersion).connectTimeout(DEFAULT_TIMEOUT).socketTimeout(DEFAULT_TIMEOUT).execute();
    final HttpResponse httpResponse = response.returnResponse();
    final Header transferEncodingHeader = httpResponse.getFirstHeader(TRANSFER_ENCODING);
    final Header contentLengthHeader = httpResponse.getFirstHeader(CONTENT_LENGTH);
    assertThat(contentLengthHeader, notNullValue());
    assertThat(transferEncodingHeader, nullValue());
    assertThat(IOUtils.toString(httpResponse.getEntity().getContent()), is(expectedBody));
  }

  protected void testResponseIsChunkedEncoding(String url, HttpVersion httpVersion, String expedtedBody) throws IOException {
    final Response response = Post(url).version(httpVersion).connectTimeout(DEFAULT_TIMEOUT).socketTimeout(DEFAULT_TIMEOUT)
        .bodyByteArray(expedtedBody.getBytes()).execute();
    final HttpResponse httpResponse = response.returnResponse();
    final Header transferEncodingHeader = httpResponse.getFirstHeader(TRANSFER_ENCODING);
    final Header contentLengthHeader = httpResponse.getFirstHeader(CONTENT_LENGTH);
    assertThat(contentLengthHeader, nullValue());
    assertThat(transferEncodingHeader, notNullValue());
    assertThat(transferEncodingHeader.getValue(), is(CHUNKED));
    assertThat(IOUtils.toString(httpResponse.getEntity().getContent()), is(expedtedBody));
  }

  protected void testResponseIsNotChunkedEncoding(String url, HttpVersion httpVersion, String expectedBody) throws IOException {
    final Response response = Post(url).version(httpVersion).connectTimeout(DEFAULT_TIMEOUT).socketTimeout(DEFAULT_TIMEOUT)
        .bodyByteArray(expectedBody.getBytes()).execute();
    final HttpResponse httpResponse = response.returnResponse();
    final Header transferEncodingHeader = httpResponse.getFirstHeader(TRANSFER_ENCODING);
    final Header contentLengthHeader = httpResponse.getFirstHeader(CONTENT_LENGTH);
    assertThat(contentLengthHeader, nullValue());
    assertThat(transferEncodingHeader, is(nullValue()));
    assertThat(IOUtils.toString(httpResponse.getEntity().getContent()), is(expectedBody));
  }

}

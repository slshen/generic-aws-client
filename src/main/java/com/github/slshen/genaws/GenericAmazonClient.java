// Copyright 2019 Sam Shen
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.github.slshen.genaws;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicReference;
import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.DefaultAWSCredentialsProviderChain;
import com.amazonaws.regions.Regions;
import com.amazonaws.retry.PredefinedRetryPolicies;
import com.amazonaws.retry.RetryPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.github.slshen.genaws.auth.AwsV4SigningInterceptor;
import okhttp3.Interceptor;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import okhttp3.logging.HttpLoggingInterceptor.Level;

public class GenericAmazonClient {
  private static final ObjectMapper mapper =
      new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
  private static final AwsXmlParser xmlParser = new AwsXmlParser();
  private static final AtomicReference<String> userAgent = new AtomicReference<>();
  private OkHttpClient httpClient;
  private AWSCredentialsProvider credentialsProvider;
  private RetryPolicy retryPolicy = PredefinedRetryPolicies.getDefaultRetryPolicy();
  private HttpLoggingInterceptor logger;

  public GenericAmazonClient() {
    this(new OkHttpClient(), new DefaultAWSCredentialsProviderChain());
  }

  public GenericAmazonClient(OkHttpClient httpClient, AWSCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;

    Interceptor userAgentInterceptor =
        new Interceptor() {
          @Override
          public Response intercept(Chain chain) throws IOException {
            return chain.proceed(
                chain.request().newBuilder().addHeader("User-Agent", getUserAgent()).build());
          }
        };
    this.httpClient =
        httpClient
            .newBuilder()
            .addInterceptor(new AwsV4SigningInterceptor(credentialsProvider))
            .addInterceptor(userAgentInterceptor)
            .addInterceptor(logger = new HttpLoggingInterceptor())
            .build();
  }

  public GenericAmazonClient(AWSCredentialsProvider credentialsProvider) {
    this(new OkHttpClient(), credentialsProvider);
  }

  public GenericAmazonClient loggerLevel(Level level) {
    logger.level(level);
    return this;
  }

  public OkHttpClient getHttpClient() {
    return httpClient;
  }

  public AWSCredentialsProvider getCredentialsProvider() {
    return credentialsProvider;
  }

  public AmazonServiceData getServiceData(Request request) {
    return request.tag(AmazonServiceData.class);
  }

  public GenericAmazonActionBuilder newActionBuilder(
      Regions region, String serviceName, String action) {
    return new GenericAmazonActionBuilder(
        region, AmazonServiceData.getServiceData(serviceName), action);
  }

  public JsonNode execute(Request request) {
    int retriesAttempted = 0;
    while (true) {
      try {
        try {
          try (Response response = httpClient.newCall(request).execute()) {
            if (response.isSuccessful()) {
              return parse(response);
            } else {
              AmazonServiceException ase;
              if (response.code() >= 500) {
                ase = new AmazonServiceException(Integer.toString(response.code()));
              } else {
                ase = parseError(response, response.body() != null ? response.body().string() : "");
              }
              ase.setStatusCode(response.code());
              ase.setServiceName(getServiceData(request).getEndpointPrefix());
              throw ase;
            }
          }
        } catch (IOException e) {
          throw new AmazonClientException(e.getMessage(), e);
        }
      } catch (AmazonClientException e) {
        if (retriesAttempted == retryPolicy.getMaxErrorRetry()
            || !retryPolicy.getRetryCondition().shouldRetry(null, e, retriesAttempted)) {
          throw e;
        }
        retryPolicy.getBackoffStrategy().delayBeforeNextRetry(null, e, retriesAttempted);
        retriesAttempted += 1;
      }
    }
  }

  private AmazonServiceException parseError(Response response, String body) {
    try {
      String protocol = getServiceData(response.request()).getProtocol();
      if (protocol.equals("query") || protocol.equals("ec2")) {
        JsonNode n = xmlParser.parse(objectNode(), new StringReader(body));
        JsonNode error = n.path("Error");
        error = error.isMissingNode() ? n.path("Errors").path("Error") : error;
        AmazonServiceException ase = new AmazonServiceException(error.path("Message").asText());
        JsonNode requestId = n.path("RequestID");
        requestId = requestId.isMissingNode() ? requestId = n.path("RequestId") : requestId;
        ase.setErrorCode(error.path("Code").asText());
        ase.setRequestId(requestId.asText());
        return ase;
      } else {
        JsonNode n = mapper.readTree(body);
        JsonNode message = n.path("Message");
        message = message.isMissingNode() ? n.path("message") : message;
        AmazonServiceException ase = new AmazonServiceException(message.asText());
        JsonNode code = n.path("__type");
        if (code.asText().contains("#")) {
          int hash = code.asText().lastIndexOf('#');
          ase.setErrorCode(code.asText().substring(hash + 1));
        } else {
          ase.setErrorCode(code.asText());
        }
        ase.setRequestId(response.header("X-Amzn-RequestId"));
        return ase;
      }

    } catch (IOException e) {
      return new AmazonServiceException("unable to parse error message", e);
    }
  }

  private JsonNode parse(Response response) throws IOException {
    AmazonServiceData service = getServiceData(response.request());
    String protocol = service.getProtocol();
    if (protocol.equals("ec2") || protocol.equals("query")) {
      return xmlParser.parse(objectNode(), response.body().charStream());
    } else {
      return mapper.readTree(response.body().charStream());
    }
  }

  public ObjectNode objectNode() {
    return mapper.createObjectNode();
  }

  public static String getUserAgent() {
    if (userAgent.get() == null) {
      Properties props = new Properties();
      try {
        InputStream in = GenericAmazonClient.class.getResourceAsStream("version.properties");
        if (in != null) {
          try {
            props.load(in);
          } finally {
            in.close();
          }
        }
      } catch (IOException e) {
      }
      String okhttpVersion = props.getProperty("okhttp.version", "UNKNOWN");
      String projectVersion = props.getProperty("project.version", "UNKNOWN");
      userAgent.set("OkHttp3/" + okhttpVersion + " generic-aws-client/" + projectVersion);
    }
    return userAgent.get();
  }

  public ObjectMapper objectMapper() {
    return mapper;
  }
}

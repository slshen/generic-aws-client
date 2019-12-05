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

package com.github.slshen.genaws.auth;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSSessionCredentials;
import com.google.common.annotations.VisibleForTesting;
import okhttp3.Request;
import okio.Buffer;

public class AwsV4Signer {
  private static final String AWS4_SIGNING_ALGORITHM = "AWS4-HMAC-SHA256";

  @VisibleForTesting static final String X_AMZ_SECURITY_TOKEN = "X-Amz-Security-Token";

  @VisibleForTesting static final String X_AMZ_DATE = "X-Amz-Date";

  private static final String AUTHORIZATION = "Authorization";

  private static final char NEWLINE = '\n';

  private final Request request;
  private String accessKeyId;
  private String secretKey;

  private String amzDate;

  private String credentialScope;

  private String signedHeaders;

  private String sessionToken;

  private byte[] kSigning;

  public AwsV4Signer(
      String regionName,
      String service,
      AWSCredentialsProvider credentialsProvider,
      Request request) {

    amzDate = request.header(X_AMZ_DATE);
    String shortDate = amzDate.substring(0, 8);
    credentialScope =
        new StringBuilder()
            .append(shortDate)
            .append('/')
            .append(regionName)
            .append('/')
            .append(service)
            .append("/aws4_request")
            .toString();

    AWSCredentials credentials = credentialsProvider.getCredentials();
    // the AWS SDK does this synchronization on credentials thing so we'll do the same
    synchronized (credentials) {
      accessKeyId = credentials.getAWSAccessKeyId().trim();
      secretKey = credentials.getAWSSecretKey().trim();
      if (credentials instanceof AWSSessionCredentials) {
        sessionToken = ((AWSSessionCredentials) credentials).getSessionToken().trim();
      }
    }

    byte[] kDate = Hashing.hmacSha256("AWS4" + secretKey, shortDate);
    byte[] kRegion = Hashing.hmacSha256(kDate, regionName);
    byte[] kService = Hashing.hmacSha256(kRegion, service);
    kSigning = Hashing.hmacSha256(kService, "aws4_request");

    this.request = request;
  }

  public Request sign() {
    Request.Builder b = request.newBuilder().addHeader(AUTHORIZATION, getAuthorizationHeader());
    if (sessionToken != null && request.header(X_AMZ_SECURITY_TOKEN) == null) {
      b.addHeader(X_AMZ_SECURITY_TOKEN, sessionToken);
    }
    return b.build();
  }

  @VisibleForTesting
  String getAuthorizationHeader() {
    StringBuilder value =
        new StringBuilder(AWS4_SIGNING_ALGORITHM)
            .append(" Credential=")
            .append(accessKeyId)
            .append('/')
            .append(credentialScope)
            .append(", SignedHeaders=")
            .append(getSignedHeaders())
            .append(", Signature=");
    addSignature(value);
    return value.toString();
  }

  private void addSignature(StringBuilder value) {
    String stringToSign = getStringToSign();
    byte[] result = Hashing.hmacSha256(kSigning, stringToSign);
    Hashing.addHexString(value, result);
  }

  @VisibleForTesting
  String getStringToSign() {
    StringBuilder value = new StringBuilder();
    value.append(AWS4_SIGNING_ALGORITHM).append(NEWLINE);
    value.append(amzDate).append(NEWLINE);
    value.append(credentialScope).append(NEWLINE);
    value.append(Hashing.hash(getCanonicalRequest()));
    return value.toString();
  }

  @VisibleForTesting
  String getCanonicalRequest() {
    StringBuilder value = new StringBuilder();
    value.append(request.method()).append(NEWLINE);
    value.append(request.url().encodedPath().replaceAll("/+", "/")).append(NEWLINE);
    addCanonicalQueryString(value).append(NEWLINE);
    addCanonicalHeaders(value).append(NEWLINE);
    value.append(getSignedHeaders()).append(NEWLINE);
    addBodyHash(value);
    return value.toString();
  }

  private void addBodyHash(StringBuilder value) {
    if (request.body() != null) {
      Buffer b = new Buffer();
      try {
        request.newBuilder().build().body().writeTo(b);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      value.append(Hashing.hash(b.readUtf8()));
    } else {
      value.append(Hashing.hash(""));
    }
  }

  private StringBuilder addCanonicalHeaders(StringBuilder value) {
    List<String> headerNames = sort(request.headers().names());
    for (String headerName : headerNames) {
      value.append(headerName.trim().toLowerCase()).append(':');
      List<String> values = request.headers().values(headerName);
      boolean first = true;
      for (String headerValue : values) {
        if (!first) {
          value.append(',');
        }
        first = false;
        value.append(headerValue.replaceAll("  *", " "));
      }
      value.append(NEWLINE);
    }
    return value;
  }

  private StringBuilder addCanonicalQueryString(StringBuilder value) {
    boolean first = true;
    for (String parameterName : sort(request.url().queryParameterNames())) {
      List<String> parameterValues = sort(request.url().queryParameterValues(parameterName));
      for (String parameterValue : parameterValues) {
        if (!first) {
          value.append('&');
        }
        first = false;
        value
            .append(rfc3986Encode(parameterName))
            .append('=')
            .append(rfc3986Encode(parameterValue));
      }
    }
    return value;
  }

  private String rfc3986Encode(String value) {
    try {
      return URLEncoder.encode(value, "utf8")
          .replace("+", "%20")
          .replace("*", "%2A")
          .replace("%7E", "~");
    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException(e);
    }
  }

  private String getSignedHeaders() {
    if (signedHeaders == null) {
      StringBuilder result = new StringBuilder();
      for (String name : sort(request.headers().names())) {
        if (result.length() > 0) {
          result.append(';');
        }
        result.append(name.trim().toLowerCase());
      }
      signedHeaders = result.toString();
    }
    return signedHeaders;
  }

  private List<String> sort(Collection<String> names) {
    List<String> result = new ArrayList<>(names);
    result.sort(String.CASE_INSENSITIVE_ORDER);
    return result;
  }
}

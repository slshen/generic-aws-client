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
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.regions.Regions;
import com.github.slshen.genaws.auth.AwsV4Signer;
import okhttp3.Request;

/** See https://docs.aws.amazon.com/general/latest/gr/signature-v4-test-suite.html */
public class AmazonTestDataTest {

  public static String accessKeyId = "AKIDEXAMPLE";
  public static String secretKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY";
  public static AWSCredentialsProvider testDataCredentialsProvider =
      new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKeyId, secretKey));
  private String sessionToken =
      "AQoDYXdzEPT//////////wEXAMPLEtc764bNrC9SAPBSM22wDOk4x4HIZ8j4FZTwdQWLWsKWHGBuFqwAeMicRXmxfpSPfIeoIYRqTflfKD8YUuwthAx7mSEI/qkPpKPi/kMcGdQrmGdeehM4IC1NtBmUpp2wUE8phUZampKsburEDy0KPkyQDYwT7WZ0wq5VSXDvp75YU9HFvlRd8Tx6q6fE8YQcHNVXAkiY9q6d+xo0rKwT38xVqr7ZD0u0iPPkUL64lIZbqBAz+scqKmlzm8FDrypNC9Yjc8fPOLn9FX9KSYvKTr4rvx3iSIlTJabIQwj2ICCR/oLxBA==";

  @Test
  public void testReadData() throws IOException {
    TestData getVanilla = new TestData("get-vanilla");
    Request request = getVanilla.request();
    Assertions.assertThat(request.method()).isEqualTo("GET");
    Assertions.assertThat(request.url().pathSegments()).containsExactly("");
    Assertions.assertThat(request.url().query()).isNull();
    Assertions.assertThat(request.header("X-Amz-Date")).isEqualTo("20150830T123600Z");
  }

  @Test
  public void testPostStsAfter() throws IOException {
    Request signedRequest =
        verify(
            "post-sts-token/post-sts-header-after",
            new AWSStaticCredentialsProvider(
                new BasicSessionCredentials(accessKeyId, secretKey, sessionToken)));
    Assertions.assertThat(signedRequest.header(AwsV4Signer.X_AMZ_SECURITY_TOKEN))
        .isEqualTo(sessionToken);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "get-header-key-duplicate",
        "get-header-value-multiline",
        "get-header-value-order",
        "get-header-value-trim",
        "get-unreserved",
        "get-utf8",
        "get-vanilla",
        "get-vanilla-empty-query-key",
        "get-vanilla-query",
        "get-vanilla-query-order-key",
        "get-vanilla-query-order-key-case",
        "get-vanilla-query-order-value",
        "get-vanilla-query-unreserved",
        "get-vanilla-utf8-query",
        "normalize-path/get-relative",
        "normalize-path/get-relative-relative",
        "normalize-path/get-slash",
        "normalize-path/get-slash-dot-slash",
        "normalize-path/get-slash-pointless-dot",
        "normalize-path/get-slashes",
        "normalize-path/get-space",
        "post-header-key-case",
        "post-header-key-sort",
        "post-header-value-case",
        "post-sts-token/post-sts-header-before",
        "post-vanilla",
        "post-vanilla-empty-query-value",
        "post-vanilla-query",
        // TODO - why do these fail?
        // "post-x-www-form-urlencoded",
        // "post-x-www-form-urlencoded-parameters",
      })
  public void testAll(String name) throws IOException {
    verify(name, testDataCredentialsProvider);
  }

  private Request verify(String name, AWSCredentialsProvider credentialsProvider)
      throws IOException {
    TestData testData = new TestData(name);
    Request request = testData.request();
    AwsV4Signer signer =
        new AwsV4Signer(Regions.US_EAST_1.getName(), "service", credentialsProvider, request);
    Assertions.assertThat(signer.getCanonicalRequest())
        .as("canonical request")
        .isEqualTo(testData.creq);
    Assertions.assertThat(signer.getStringToSign()).as("string to sign").isEqualTo(testData.sts);
    Assertions.assertThat(signer.getAuthorizationHeader())
        .as("authorization header")
        .isEqualTo(testData.authz);
    return signer.sign();
  }
}

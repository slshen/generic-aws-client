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

import java.io.File;
import java.io.IOException;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.regions.Regions;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JsonNode;
import okhttp3.logging.HttpLoggingInterceptor.Level;

public class GenericAmazonClientTest {

  @BeforeEach
  public void assumptions() {
    String accessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
    String home = System.getProperty("user.home");
    File awsDir = home != null ? new File(home, ".aws") : null;
    Assumptions.assumeTrue(accessKeyId != null || awsDir.isDirectory());
  }

  @Test
  public void testVersion() {
    Assertions.assertThat(GenericAmazonClient.getUserAgent()).isNotNull();
  }

  @Test
  public void testGetCallerIdentity() throws IOException {
    GenericAmazonClient client = new GenericAmazonClient();
    JsonNode n =
        client.execute(
            client.newActionBuilder(Regions.US_EAST_1, "sts", "GetCallerIdentity").build());
    System.out.println(n);
  }

  @Test
  public void testLambdaListFunctions() {
    GenericAmazonClient client = new GenericAmazonClient();
    JsonNode n =
        client.execute(
            client
                .newActionBuilder(Regions.US_WEST_2, "lambda", "ListFunctions")
                .path("/2015-03-31/functions")
                .parameters(client.objectNode().put("FunctionVersion", "ALL"))
                .method("GET")
                .build());
    System.out.println(n);
  }

  @Test
  public void testKinesisListStreams()
      throws JsonGenerationException, JsonMappingException, IOException {
    GenericAmazonClient client = new GenericAmazonClient();
    JsonNode n =
        client.execute(
            client
                .newActionBuilder(Regions.US_WEST_2, "kinesis", "ListStreams")
                .parameters(client.objectNode().put("Limit", 1))
                .build());
    System.out.println(n);
  }

  @Test
  public void testDescribeSubnets()
      throws JsonGenerationException, JsonMappingException, IOException {
    GenericAmazonClient client = new GenericAmazonClient().loggerLevel(Level.BASIC);
    JsonNode n =
        client.execute(
            client
                .newActionBuilder(Regions.US_WEST_2, "ec2", "DescribeSubnets")
                .parameters(client.objectNode().put("MaxResults", 5))
                .build());
    client.objectMapper().writeValue(System.out, n);
  }

  @Test
  public void testEc2Error() {
    GenericAmazonClient client = new GenericAmazonClient().loggerLevel(Level.HEADERS);
    Assertions.assertThatThrownBy(
            () -> {
              client.execute(
                  client
                      .newActionBuilder(Regions.US_WEST_2, "ec2", "DescribeImageAttribute")
                      .build());
            })
        .isInstanceOf(AmazonServiceException.class);
  }

  @Test
  public void testKinesisError() {
    GenericAmazonClient client = new GenericAmazonClient().loggerLevel(Level.BASIC);
    Assertions.assertThatThrownBy(
            () -> {
              client.execute(
                  client.newActionBuilder(Regions.US_WEST_2, "kinesis", "PutRecord").build());
            })
        .isInstanceOf(AmazonServiceException.class);
  }
}

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
import java.time.Clock;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.regions.Region;
import com.github.slshen.genaws.AmazonServiceData;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

public class AwsV4SigningInterceptor implements Interceptor {

  private static final DateTimeFormatter timeFormatter =
      DateTimeFormat.forPattern("yyyyMMdd'T'HHmmss'Z'").withZoneUTC();
  private final AWSCredentialsProvider credentialsProvider;
  private Clock clock = Clock.systemDefaultZone();

  public AwsV4SigningInterceptor(AWSCredentialsProvider credentialsProvider) {
    this.credentialsProvider = credentialsProvider;
  }

  @Override
  public Response intercept(Chain chain) throws IOException {
    Request request = chain.request();
    Region regionData = request.tag(Region.class);
    AmazonServiceData serviceData = request.tag(AmazonServiceData.class);
    if (regionData != null && serviceData != null) {
      Request.Builder builder = request.newBuilder();
      String amzDate = request.header(AwsV4Signer.X_AMZ_DATE);
      if (amzDate == null) {
        amzDate = timeFormatter.print(clock.millis());
        builder.addHeader(AwsV4Signer.X_AMZ_DATE, amzDate);
      }
      if (request.header("Host") == null) {
        builder.addHeader("Host", request.url().host());
      }
      request =
          new AwsV4Signer(
                  regionData.getName(),
                  serviceData.getEndpointPrefix(),
                  credentialsProvider,
                  builder.build())
              .sign();
    }
    return chain.proceed(request);
  }
}

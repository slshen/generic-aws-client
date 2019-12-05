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

import java.util.Iterator;
import java.util.function.BiConsumer;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.google.common.base.Strings;
import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class GenericAmazonActionBuilder {

  private static final String X_AMZ_TARGET = "X-Amz-Target";
  private Regions region;
  private AmazonServiceData service;
  private String action;
  private String path;
  private JsonNode parameters = MissingNode.getInstance();
  private String method = "POST";

  public GenericAmazonActionBuilder(Regions region, AmazonServiceData service, String action) {
    this.region = region;
    this.service = service;
    this.action = action;
  }

  public GenericAmazonActionBuilder path(String path) {
    this.path = path;
    return this;
  }

  public GenericAmazonActionBuilder parameters(JsonNode parameters) {
    this.parameters = parameters;
    return this;
  }

  public GenericAmazonActionBuilder method(String method) {
    this.method = method;
    return this;
  }

  public Request build() {
    Region regionData = Region.getRegion(region);
    HttpUrl url = getUrl(regionData);
    Request.Builder builder =
        new Request.Builder().tag(Region.class, regionData).tag(AmazonServiceData.class, service);
    String protocol = service.getProtocol();
    if (service.getProtocol().equals("json")) {
      builder.addHeader(X_AMZ_TARGET, service.getTargetPrefix() + "." + action);
    }
    RequestBody body = null;
    if (protocol.equals("query") || protocol.equals("ec2")) {
      if (method.equals("POST")) {
        FormBody.Builder bodyBuilder = new FormBody.Builder();
        bodyBuilder.add("Action", action);
        bodyBuilder.add("Version", service.getApiVersion());
        flatten(bodyBuilder::add, parameters, null);
        body = bodyBuilder.build();
      } else {
        HttpUrl.Builder urlBuilder = url.newBuilder();
        urlBuilder.addQueryParameter("Action", action);
        urlBuilder.addQueryParameter("Version", service.getApiVersion());
        flatten(urlBuilder::addQueryParameter, parameters, null);
        url = urlBuilder.build();
      }
    } else if (protocol.equals("rest-json") || protocol.equals("json")) {
      if (method.equals("POST")) {
        body =
            RequestBody.create(
                parameters.isObject() ? parameters.toString() : "{}",
                MediaType.get("application/x-amz-json-" + service.getJsonVersion()));
      } else {
        HttpUrl.Builder urlBuilder = url.newBuilder();
        flatten(urlBuilder::addQueryParameter, parameters, null);
        url = urlBuilder.build();
      }
    } else {
      throw new IllegalStateException("unknown protocol " + protocol);
    }
    return builder.method(method, body).url(url).build();
  }

  private HttpUrl getUrl(Region regionData) {
    return new HttpUrl.Builder()
        .host(regionData.getServiceEndpoint(service.getEndpointPrefix()))
        .addPathSegments(Strings.nullToEmpty(path))
        .scheme("https")
        .build();
  }

  private void flatten(BiConsumer<String, String> setter, JsonNode param, String name) {
    if (param.isObject()) {
      for (Iterator<String> iter = param.fieldNames(); iter.hasNext(); ) {
        String field = iter.next();
        JsonNode value = param.get(field);
        flatten(setter, value, name == null ? field : name + "." + field);
      }
    } else if (param.isArray()) {
      for (int i = 0; i < param.size(); i++) {
        flatten(setter, param.get(i), name + "." + (i + 1));
      }
    } else if (!param.isMissingNode()) {
      setter.accept(name, param.asText());
    }
  }
}

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
import java.util.HashMap;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.MissingNode;

public class AmazonServiceData {
  private static Map<String, AmazonServiceData> services;
  private JsonNode metadata;
  private static final AmazonServiceData UNLOADED =
      new AmazonServiceData(MissingNode.getInstance());

  public AmazonServiceData(JsonNode metadata) {
    this.metadata = metadata;
  }

  public String getEndpointPrefix() {
    return metadata.path("endpointPrefix").asText();
  }

  public String getApiVersion() {
    return metadata.path("apiVersion").asText();
  }

  public String getProtocol() {
    return metadata.path("protocol").asText();
  }

  public static synchronized AmazonServiceData getServiceData(String serviceName) {
    ObjectMapper m = new ObjectMapper();
    try {
      if (services == null) {
        services = new HashMap<>();
        InputStream in = AmazonServiceData.class.getResourceAsStream("data/services.json");
        try {
          for (JsonNode n : m.readTree(in)) {
            services.put(n.asText(), UNLOADED);
          }
        } finally {
          in.close();
        }
      }
      if (!services.containsKey(serviceName)) {
        throw new IllegalArgumentException("unknown service " + serviceName);
      }
      AmazonServiceData service = services.get(serviceName);
      if (service == UNLOADED) {
        InputStream in =
            AmazonServiceData.class.getResourceAsStream("data/" + serviceName + "-metadata.json");
        try {
          service = new AmazonServiceData(m.readTree(in));
        } finally {
          in.close();
        }
        services.put(serviceName, service);
      }
      return service;
    } catch (IOException e) {
      throw new RuntimeException("corrupt service metadata", e);
    }
  }

  public String getTargetPrefix() {
    return metadata.path("targetPrefix").asText();
  }

  public String getJsonVersion() {
    return metadata.path("jsonVersion").asText();
  }
}

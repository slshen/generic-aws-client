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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Assertions;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;

public class TestData {
  public String name, authz, creq, req, sts;

  public TestData(String name) {
    this.name = name;
    authz = load("authz");
    creq = load("creq");
    req = load("req");
    //    sreq = load("sreq");
    sts = load("sts");
  }

  public Request request() throws IOException {
    try (BufferedReader r = new BufferedReader(new StringReader(req))) {
      Request.Builder b = new Request.Builder();
      String line = r.readLine();
      // method query-string proto
      Matcher m = Pattern.compile("^(\\S+) (.+) HTTP/[0-9.]+$").matcher(line);
      if (!m.lookingAt()) {
        throw new IllegalStateException();
      }
      String method = m.group(1);
      String pathAndQuery = m.group(2);
      // String proto = m.group(3);
      String host = null;
      String headerName = null;
      while ((line = r.readLine()) != null) {
        if (line.length() == 0) {
          break;
        }
        int colon = line.indexOf(':');
        if (colon > 0) {
          headerName = line.substring(0, colon);
        }
        String headerValue = line.substring(colon + 1);
        b.addHeader(headerName, headerValue);
        if ("Host".equalsIgnoreCase(headerName)) {
          host = headerValue;
        }
      }
      b.url("https://" + host + pathAndQuery);
      RequestBody body;
      if (line != null) {
        StringBuilder bodyString = new StringBuilder();
        while ((line = r.readLine()) != null) {
          if (bodyString.length() > 0) {
            bodyString.append('\n');
          }
          bodyString.append(line);
        }
        body =
            RequestBody.create(
                bodyString.toString(), MediaType.get("application/x-www-form-urlencoded"));
      } else {
        body = RequestBody.create(new byte[0]);
      }
      if (method.equals("POST")) {
        b.post(body);
      } else {
        b.get();
      }
      return b.build();
    }
  }

  private String load(String ext) {
    int slash = name.lastIndexOf('/');
    String basename = slash > 0 ? name.substring(slash + 1) : name;
    InputStream in =
        getClass()
            .getResourceAsStream(
                "/aws-sig-v4-test-suite/aws-sig-v4-test-suite/"
                    + name
                    + "/"
                    + basename
                    + "."
                    + ext);
    Assertions.assertNotNull(in, "amazon test suite data");
    try {
      try {
        StringBuilder s = new StringBuilder();
        char[] buf = new char[1024];
        InputStreamReader r = new InputStreamReader(in, "utf8");
        int n;
        while ((n = r.read(buf)) > 0) {
          s.append(buf, 0, n);
        }
        return s.toString();
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}

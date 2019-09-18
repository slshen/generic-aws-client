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

import java.nio.charset.Charset;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class Hashing {

  private static final Charset UTF8 = Charset.forName("UTF-8");
  private static final String MAC_ALGORITHM = "HmacSHA256";

  public static byte[] hmacSha256(String key, String data) {

    return hmacSha256(key.getBytes(UTF8), data);
  }

  public static byte[] hmacSha256(byte[] key, String data) {
    try {
      Mac sha256Hmac = Mac.getInstance(MAC_ALGORITHM);
      SecretKeySpec secretKey = new SecretKeySpec(key, MAC_ALGORITHM);
      sha256Hmac.init(secretKey);
      return sha256Hmac.doFinal(data.getBytes(UTF8));
    } catch (InvalidKeyException | NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static String hash(String value) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] d = md.digest(value.getBytes(UTF8));
      StringBuilder b = new StringBuilder();
      addHexString(b, d);
      return b.toString();
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }

  public static void addHexString(StringBuilder value, byte[] result) {
    for (int i = 0; i < result.length; i++) {
      value.append(String.format("%02x", result[i] & 0xff));
    }
  }
}

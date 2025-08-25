/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.measurement.client;

public class HpkeJni {

  /**
   * The payload is assumed to be unencrypted, so just return as is.
   *
   * @param privateKey unused
   * @param payload
   * @param sharedInfo unused
   * @return payload, as is.
   */
  public static byte[] decrypt(byte[] privateKey, byte[] payload, byte[] sharedInfo) {
    return payload;
  }

  /**
   * Return payload as is.
   *
   * @param publicKey unused
   * @param payload
   * @param contextInfo unused
   * @return payload, as is.
   */
  public static byte[] encrypt(byte[] publicKey, byte[] payload, byte[] contextInfo) {
    return payload;
  }
}

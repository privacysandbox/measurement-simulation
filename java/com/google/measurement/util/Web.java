/*
 * Copyright 2022 Google LLC
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

package com.google.measurement.util;

import com.google.common.net.InternetDomainName;
import java.net.URI;
import java.util.Optional;

/** Filtering utilities for measurement. */
public final class Web {

  private Web() {}

  /**
   * Returns a {@code URI} of the scheme concatenated with the first subdomain of the provided URL
   * that is beneath the public suffix.
   *
   * @param uri the URI to parse.
   */
  public static Optional<URI> topPrivateDomainAndScheme(URI uri) {
    String scheme = uri.getScheme();
    String host = uri.getHost();
    if (scheme == null || host == null) {
      return Optional.empty();
    }
    try {
      InternetDomainName domainName = InternetDomainName.from(host);
      String url = scheme + "://" + domainName.topPrivateDomain();
      return Optional.of(URI.create(url));
    } catch (IllegalArgumentException | IllegalStateException e) {
      return Optional.empty();
    }
  }

  /**
   * Returns a {@code URI} of the scheme concatenated with the first subdomain of the provided URL
   * that is beneath the public suffix.
   *
   * @param uri the URL string to parse.
   */
  public static Optional<URI> topPrivateDomainAndScheme(String uri) {
    try {
      URI inputUri = URI.create(uri);
      return topPrivateDomainAndScheme(inputUri);
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }
}

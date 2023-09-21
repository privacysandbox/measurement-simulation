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

package com.google.measurement;

import com.google.measurement.util.Util;
import java.net.URI;
import java.util.Objects;
import org.apache.beam.sdk.coders.AvroCoder;
import org.apache.beam.sdk.coders.DefaultCoder;
import org.json.simple.JSONObject;

@DefaultCoder(AvroCoder.class)
public class ExtensionEvent {
  private String action;
  private URI uri;
  private long timestamp;

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ExtensionEvent)) {
      return false;
    }

    ExtensionEvent other = (ExtensionEvent) o;
    return Objects.equals(action, other.action)
        && Objects.equals(uri, other.uri)
        && timestamp == other.timestamp;
  }

  @Override
  public int hashCode() {
    return Objects.hash(action, uri, timestamp);
  }

  public String getAction() {
    return this.action;
  }

  public void setAction(String action) {
    this.action = action;
  }

  public URI getUri() {
    return this.uri;
  }

  public void setURI(URI uri) {
    this.uri = uri;
  }

  public long getTimestamp() {
    return this.timestamp;
  }

  public void setTimestamp(long timestamp) {
    this.timestamp = timestamp;
  }

  public static ExtensionEvent buildExtensionEventFromJson(JSONObject extensionEvent) {
    ExtensionEvent event = new ExtensionEvent();
    event.setAction((String) extensionEvent.get("action"));
    event.setURI(URI.create((String) extensionEvent.get("uri")));

    long timestamp = Util.parseJsonLong(extensionEvent, "timestamp");
    event.setTimestamp(timestamp);
    return event;
  }
}

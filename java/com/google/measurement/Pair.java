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

import java.io.Serializable;
import java.util.Objects;

public class Pair<K, V> implements Serializable {
  public final K first;
  public final V second;

  public Pair(K key, V value) {
    this.first = key;
    this.second = value;
  }

  public K getKey() {
    return first;
  }

  public V getValue() {
    return second;
  }

  public String encodeAsList() {
    return "[" + first + "," + second + "]";
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof Pair)) {
      return false;
    }
    Pair<K, V> t = (Pair<K, V>) obj;
    return Objects.equals(first, t.first) && Objects.equals(second, t.second);
  }

  @Override
  public int hashCode() {
    return Objects.hash(first, second);
  }
}

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

package com.google.measurement.client.data;

import java.util.HashMap;
import java.util.Map.Entry;
import java.util.Set;

public class ContentValues {

  HashMap<String, Object> mMap;

  ContentValues() {
    mMap = new HashMap<>();
  }

  public void put(String key, String value) {
    mMap.put(key, value);
  }

  public void put(String key, Integer value) {
    mMap.put(key, value);
  }

  public void put(String key, Long value) {
    mMap.put(key, value);
  }

  public void put(String key, Float value) {
    mMap.put(key, value);
  }

  public void put(String key, Double value) {
    mMap.put(key, value);
  }

  public void put(String key, Boolean value) {
    mMap.put(key, value);
  }

  public void put(String key, byte[] value) {
    mMap.put(key, value);
  }

  public int size() {
    return mMap.size();
  }

  public boolean isEmpty() {
    return mMap.isEmpty();
  }

  public Object get(String key) {
    return mMap.get(key);
  }

  public String getAsString(String key) {
    Object value = mMap.get(key);
    return value != null ? value.toString() : null;
  }

  public Long getAsLong(String key) {
    Object value = mMap.get(key);
    try {
      return value != null ? ((Number) value).longValue() : null;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        try {
          return Long.valueOf(value.toString());
        } catch (NumberFormatException e2) {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public Integer getAsInteger(String key) {
    Object value = mMap.get(key);
    try {
      return value != null ? ((Number) value).intValue() : null;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        try {
          return Integer.valueOf(value.toString());
        } catch (NumberFormatException e2) {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public Short getAsShort(String key) {
    Object value = mMap.get(key);
    try {
      return value != null ? ((Number) value).shortValue() : null;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        try {
          return Short.valueOf(value.toString());
        } catch (NumberFormatException e2) {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public Byte getAsByte(String key) {
    Object value = mMap.get(key);
    try {
      return value != null ? ((Number) value).byteValue() : null;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        try {
          return Byte.valueOf(value.toString());
        } catch (NumberFormatException e2) {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public Double getAsDouble(String key) {
    Object value = mMap.get(key);
    try {
      return value != null ? ((Number) value).doubleValue() : null;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        try {
          return Double.valueOf(value.toString());
        } catch (NumberFormatException e2) {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public Float getAsFloat(String key) {
    Object value = mMap.get(key);
    try {
      return value != null ? ((Number) value).floatValue() : null;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        try {
          return Float.valueOf(value.toString());
        } catch (NumberFormatException e2) {
          return null;
        }
      } else {
        return null;
      }
    }
  }

  public Boolean getAsBoolean(String key) {
    Object value = mMap.get(key);
    try {
      return (Boolean) value;
    } catch (ClassCastException e) {
      if (value instanceof CharSequence) {
        // Note that we also check against 1 here because SQLite's internal representation
        // for booleans is an integer with a value of 0 or 1. Without this check, boolean
        // values obtained via DatabaseUtils#cursorRowToContentValues will always return
        // false.
        return Boolean.valueOf(value.toString()) || "1".equals(value);
      } else if (value instanceof Number) {
        return ((Number) value).intValue() != 0;
      } else {
        return null;
      }
    }
  }

  public byte[] getAsByteArray(String key) {
    Object value = mMap.get(key);
    if (value instanceof byte[]) {
      return (byte[]) value;
    } else {
      return null;
    }
  }

  public Set<Entry<String, Object>> valueSet() {
    return mMap.entrySet();
  }

  public Set<String> keySet() {
    return mMap.keySet();
  }

  public void putNull(String key) {
    mMap.put(key, null);
  }
}

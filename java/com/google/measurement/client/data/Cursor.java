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

package com.google.measurement.client.data;

import java.io.Closeable;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Cursor implements Closeable {

  // A col_offset is necessary to adapt calls intended for Cursor to calls for ResultSet because
  // Cursor is 0-based whereas ResultSet is 1-based when referencing columns.
  public static final int COL_OFFSET = 1;
  private Map<String, Integer> mGlobalColumnIndexCache;
  private PreparedStatement mStatement;
  private ResultSet mResultSet;
  private int mCount;

  public Cursor(PreparedStatement statement, String sql, GlobalCursorCache globalCursorCache) {
    try {
      mStatement = statement;

      ResultSet tempResults = mStatement.executeQuery();
      while (tempResults.next()) {
        mCount++;
      }
      tempResults.close();

      mResultSet = statement.executeQuery();
      mGlobalColumnIndexCache = globalCursorCache.getOrCreateColumnIndexCache(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public String getString(Integer columnIdx) {
    try {
      return mResultSet.getString(columnIdx + COL_OFFSET);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean isNull(int index) {
    try {
      return mResultSet.getObject(index + COL_OFFSET) == null;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public int getInt(Integer columnIdx) {
    try {
      return mResultSet.getInt(columnIdx + COL_OFFSET);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public Double getDouble(Integer columnIdx) {
    try {
      return mResultSet.getDouble(columnIdx + COL_OFFSET);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public long getLong(Integer columnIdx) {
    try {
      return mResultSet.getLong(columnIdx + COL_OFFSET);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public boolean moveToNext() {
    try {
      return mResultSet.next();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() {
    try {
      mResultSet.close();
      mStatement.close();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  /**
   * Get the count of rows in the results.
   *
   * @return
   */
  public int getCount() {
    return mCount;
  }

  public Integer getColumnIndex(String columnName) {
    try {
      if (!mGlobalColumnIndexCache.containsKey(columnName)) {
        ResultSetMetaData metaData = mResultSet.getMetaData();

        // i = 0 because Cursor is 0 based when handling columns.
        for (int i = 0; i < metaData.getColumnCount(); i++) {
          if (metaData.getColumnName(i + COL_OFFSET).equals(columnName)) {
            mGlobalColumnIndexCache.put(columnName, i);
          }
        }
      }

      return mGlobalColumnIndexCache.get(columnName);
    } catch (SQLException e) {
      return 0;
    }
  }

  public Integer getColumnIndexOrThrow(String columnName) {
    return this.getColumnIndex(columnName);
  }

  public boolean moveToFirst() {
    try {
      // ResultSet does not actually implement a moveToFirst() so we call next() assuming the call
      // to moveToFirst() is performed before any other calls to the cursor. If moveToNext() is
      // called on the cursor before this method, the expected results will NOT be returned.
      return mResultSet.next();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public String[] getColumnNames() {
    try {
      ResultSetMetaData rsmd = mResultSet.getMetaData();
      int columnCount = rsmd.getColumnCount();

      List<String> results = new ArrayList<>();
      for (int i = COL_OFFSET; i <= columnCount; i++) {
        results.add(rsmd.getColumnName(i));
      }

      return results.toArray(new String[0]);
    } catch (SQLException e) {
      return null;
    }
  }

  public int getPosition() {
    try {
      return mResultSet.getRow();
    } catch (SQLException e) {
      return 0;
    }
  }
}

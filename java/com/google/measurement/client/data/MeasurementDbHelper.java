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

import java.sql.SQLException;
import java.util.Arrays;

public class MeasurementDbHelper {

  private SQLiteDatabase mDb;
  private static MeasurementDbHelper sSingleton = null;

  public MeasurementDbHelper() {
    try {
      mDb = new SQLiteDatabase(new GlobalCursorCache());
      mDb.execQuery("PRAGMA foreign_keys=ON");
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }

    createSchema(mDb);
  }

  public static MeasurementDbHelper getInstance() {
    synchronized (MeasurementDbHelper.class) {
      if (sSingleton == null) {
        sSingleton = new MeasurementDbHelper();
      }
      return sSingleton;
    }
  }

  public SQLiteDatabase safeGetWritableDatabase() {
    return mDb;
  }

  private void createSchema(SQLiteDatabase db) {
    MeasurementTables.CREATE_STATEMENTS.forEach(db::execQuery);
    Arrays.stream(MeasurementTables.CREATE_INDEXES).forEach(db::execQuery);
  }

  public long getDbFileSize() {
    return 0;
  }

  public SQLiteDatabase getReadableDatabase() {
    return mDb;
  }

  public SQLiteDatabase getWritableDatabase() {
    return mDb;
  }
}

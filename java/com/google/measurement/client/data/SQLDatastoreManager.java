/*
 * Copyright (C) 2022 Google LLC
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

import com.google.measurement.client.stats.AdServicesErrorLogger;
import com.google.measurement.client.stats.AdServicesErrorLoggerImpl;
import com.google.measurement.client.FlagsFactory;
import com.google.measurement.client.ITransaction;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.VisibleForTesting;

/** Datastore manager for SQLite database. */
@VisibleForTesting(visibility = VisibleForTesting.Visibility.PACKAGE)
public class SQLDatastoreManager extends DatastoreManager {

  private final MeasurementDbHelper mDbHelper;
  private static SQLDatastoreManager sSingleton;

  /** Acquire an instance of {@link SQLDatastoreManager}. */
  static synchronized SQLDatastoreManager getInstance() {
    if (sSingleton == null) {
      sSingleton = new SQLDatastoreManager();
    }
    return sSingleton;
  }

  @VisibleForTesting
  SQLDatastoreManager() {
    super(AdServicesErrorLoggerImpl.getInstance());
    mDbHelper = MeasurementDbHelper.getInstance();
  }

  @VisibleForTesting
  public SQLDatastoreManager(
      @NonNull MeasurementDbHelper dbHelper, AdServicesErrorLogger errorLogger) {
    super(errorLogger);
    mDbHelper = dbHelper;
  }

  @Override
  @Nullable
  protected ITransaction createNewTransaction() {
    SQLiteDatabase db = mDbHelper.safeGetWritableDatabase();
    if (db == null) {
      return null;
    }
    return new SQLTransaction(db);
  }

  @Override
  @VisibleForTesting
  public IMeasurementDao getMeasurementDao() {
    return new MeasurementDao(
        () -> mDbHelper.getDbFileSize() >= FlagsFactory.getFlags().getMeasurementDbSizeLimit(),
        () -> FlagsFactory.getFlags().getMeasurementReportingRetryLimit(),
        () -> FlagsFactory.getFlags().getMeasurementReportingRetryLimitEnabled());
  }

  @Override
  protected int getDataStoreVersion() {
    return mDbHelper.getReadableDatabase().getVersion();
  }
}

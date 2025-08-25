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

import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public class SQLiteDatabase {
  public static final int CONFLICT_REPLACE = 5;
  private final GlobalCursorCache mGlobalCursorCache;
  Connection mConnection;
  boolean mInTransaction = false;
  boolean mTransactionSuccessful = false;

  SQLiteDatabase(GlobalCursorCache globalCursorCache) throws SQLException {
    mGlobalCursorCache = globalCursorCache;
    mConnection = DriverManager.getConnection("jdbc:sqlite::memory:");
  }

  public void createTables(String createTableSqlCmd) {
    try {
      Statement stmt = mConnection.createStatement();
      // create a new table
      stmt.execute(createTableSqlCmd);
    } catch (SQLException e) {
      System.out.println(e.getMessage());
    }
  }

  public boolean inTransaction() {
    return mInTransaction;
  }

  public void setTransactionSuccessful() {
    mTransactionSuccessful = true;
  }

  public void beginTransaction() {
    mInTransaction = true;
    this.execQuery("BEGIN EXCLUSIVE;");
  }

  public void endTransaction() {
    try {
      if (mTransactionSuccessful) {
        this.execQuery("COMMIT;");
      } else {
        this.execQuery("ROLLBACK;");
      }
      mInTransaction = false;
      // reset this so that subsequent transactions with the same database don't incorrectly
      // assume a successful transaction.
      mTransactionSuccessful = false;
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  public static final String[] CONFLICT_VALUES =
      new String[] {"", " OR ROLLBACK ", " OR ABORT ", " OR FAIL ", " OR IGNORE ", " OR REPLACE "};

  public int insert(String tableName, String nullColumnHack, ContentValues values) {
    return insert(tableName, nullColumnHack, values, 0);
  }

  public long insertWithOnConflict(
      @NonNull String table,
      @Nullable String nullColumnHack,
      @Nullable ContentValues initialValues,
      int conflictAlgorithm) {
    return insert(table, null, initialValues, conflictAlgorithm);
  }

  public int insert(
      String tableName, String nullColumnHack, ContentValues values, int conflictAlgorithm) {
    try {
      StringBuilder sql = new StringBuilder();
      sql.append("INSERT");
      sql.append(CONFLICT_VALUES[conflictAlgorithm]);
      sql.append(" INTO ");
      sql.append(tableName);
      sql.append("(");
      Object[] bindArgs = null;
      int size = (values != null && !values.isEmpty()) ? values.size() : 0;
      if (size > 0) {
        bindArgs = new Object[size];
        int i = 0;
        for (String colName : values.keySet()) {
          sql.append((i > 0) ? "," : "");
          sql.append(colName);
          bindArgs[i++] = values.get(colName);
        }
        sql.append(") VALUES (");
        for (i = 0; i < size; ++i) {
          sql.append((i > 0) ? ",?" : "?");
        }
      } else {
        sql.append(") VALUES (NULL");
      }
      sql.append(")");
      PreparedStatement pStmt = mConnection.prepareStatement(sql.toString());
      for (int i = 0; i < size; ++i) {
        pStmt.setObject(i + 1, bindArgs[i]);
      }

      int count = pStmt.executeUpdate();
      pStmt.close();
      return count;
    } catch (SQLException e) {
      System.out.println(e);
      return -1;
    }
  }

  public int update(
      String tableName, ContentValues values, String whereClause, String[] whereArgs) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("Empty values");
    }
    try {

      StringBuilder sql = new StringBuilder();
      sql.append("UPDATE ");
      sql.append(tableName);
      sql.append(" SET ");
      // move all bind args to one array
      int setValuesSize = values.size();
      int bindArgsSize = (whereArgs == null) ? setValuesSize : (setValuesSize + whereArgs.length);
      Object[] bindArgs = new Object[bindArgsSize];
      int i = 0;
      for (String colName : values.keySet()) {
        sql.append((i > 0) ? "," : "");
        sql.append(colName);
        bindArgs[i++] = values.get(colName);
        sql.append("=?");
      }
      if (whereArgs != null) {
        for (i = setValuesSize; i < bindArgsSize; i++) {
          bindArgs[i] = whereArgs[i - setValuesSize];
        }
      }

      if (whereClause != null && !whereClause.isEmpty()) {
        sql.append(" WHERE ");
        sql.append(whereClause);
      }

      PreparedStatement pStmt = mConnection.prepareStatement(sql.toString());
      for (i = 0; i < bindArgsSize; ++i) {
        pStmt.setObject(i + 1, bindArgs[i]);
      }

      int count = pStmt.executeUpdate();
      pStmt.close();
      return count;
    } catch (SQLException e) {
      System.out.println(e);
      return -1;
    }
  }

  public int delete(String tableName, String whereClause, String[] whereArgs) {
    try {
      String sql =
          "DELETE FROM "
              + tableName
              + (whereClause != null && !whereClause.isEmpty() ? " WHERE " + whereClause : "");
      final int whereArgsSize = whereArgs != null ? whereArgs.length : 0;

      PreparedStatement pStmt = mConnection.prepareStatement(sql.toString());
      for (int i = 0; i < whereArgsSize; ++i) {
        pStmt.setObject(i + 1, whereArgs[i]);
      }
      int count = pStmt.executeUpdate();
      pStmt.close();
      return count;
    } catch (SQLException e) {
      System.out.println(e);
      return -1;
    }
  }

  public Cursor query(
      boolean distinct,
      String table,
      String[] columns,
      String selection,
      String[] selectionArgs,
      String groupBy,
      String having,
      String orderBy,
      String limit) {
    try {
      String sql =
          SQLiteQueryBuilder.buildQueryString(
              distinct, table, columns, selection, groupBy, having, orderBy, limit);
      PreparedStatement pStmt = mConnection.prepareStatement(sql);
      if (selectionArgs != null) {
        for (int i = 0; i < selectionArgs.length; ++i) {
          pStmt.setObject(i + 1, selectionArgs[i]);
        }
      }
      Cursor cursor = new Cursor(pStmt, sql, mGlobalCursorCache);

      return cursor;
    } catch (SQLException e) {
      System.out.println(e);
      return null;
    }
  }

  public Cursor query(
      String table,
      String[] columns,
      String selection,
      String[] selectionArgs,
      String groupBy,
      String having,
      String orderBy,
      String limit) {
    return query(false, table, columns, selection, selectionArgs, groupBy, having, orderBy, limit);
  }

  public Cursor rawQuery(String sql, String[] selectionArgs) {
    return new Cursor(execQuery(sql, selectionArgs), sql, mGlobalCursorCache);
  }

  public Cursor query(
      String table,
      String[] columns,
      String selection,
      String[] selectionArgs,
      String groupBy,
      String having,
      String orderBy) {
    return query(table, columns, selection, selectionArgs, groupBy, having, orderBy, null);
  }

  public int getVersion() {
    return 0;
  }

  public void execQuery(String s) {
    try (PreparedStatement pStmt = mConnection.prepareStatement(s)) {
      pStmt.execute();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public PreparedStatement execQuery(String s, String[] selectionArgs) {
    try {
      PreparedStatement pStmt = mConnection.prepareStatement(s);
      if (selectionArgs != null) {
        for (int i = 0; i < selectionArgs.length; ++i) {
          pStmt.setObject(i + 1, selectionArgs[i]);
        }
      }

      return pStmt;
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  public long execQueryForLong(String query, String[] selectionArgs) {
    try (PreparedStatement statement = execQuery(query, selectionArgs);
        ResultSet resultSet = statement.executeQuery()) {
      return resultSet.getLong(1);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }
}

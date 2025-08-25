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

public class SQLiteQueryBuilder {

  public static String buildQueryString(
      boolean distinct,
      String tables,
      String[] columns,
      String where,
      String groupBy,
      String having,
      String orderBy,
      String limit) {
    if ((groupBy == null || groupBy.isEmpty()) && (having != null && !having.isEmpty())) {
      throw new IllegalArgumentException(
          "HAVING clauses are only permitted when using a groupBy clause");
    }

    StringBuilder query = new StringBuilder();

    query.append("SELECT ");
    if (distinct) {
      query.append("DISTINCT ");
    }

    if (columns != null && columns.length != 0) {
      appendColumns(query, columns);
    } else {
      query.append("* ");
    }
    query.append("FROM ");
    query.append(tables);
    appendClause(query, " WHERE ", where);
    appendClause(query, " GROUP BY ", groupBy);
    appendClause(query, " HAVING ", having);
    appendClause(query, " ORDER BY ", orderBy);
    appendClause(query, " LIMIT ", limit);

    return query.toString();
  }

  private static void appendClause(StringBuilder s, String name, String clause) {
    if (clause != null && !clause.isEmpty()) {
      s.append(name);
      s.append(clause);
    }
  }

  /** Add the names that are non-null in columns to s, separating them with commas. */
  public static void appendColumns(StringBuilder s, String[] columns) {
    int n = columns.length;

    for (int i = 0; i < n; i++) {
      String column = columns[i];

      if (column != null) {
        if (i > 0) {
          s.append(", ");
        }
        s.append(column);
      }
    }
    s.append(' ');
  }
}

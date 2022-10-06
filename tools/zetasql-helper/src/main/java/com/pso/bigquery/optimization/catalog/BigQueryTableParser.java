/*
 * Copyright 2022 Google LLC All Rights Reserved
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.pso.bigquery.optimization.catalog;

import com.google.api.services.bigquery.model.TableReference;
import com.google.cloud.bigquery.Table;
import com.google.cloud.bigquery.TableId;
import com.pso.bigquery.optimization.exceptions.InvalidTableReference;
import io.vavr.control.Try;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Utility class to parse a table reference into a TableReference object
public class BigQueryTableParser {

  private static final String PROJECT_ID_TAG = "projectId";
  private static final String DATASET_ID_TAG = "dataset";
  private static final String TABLE_ID_TAG = "table";

  private static final String PROJECT_PATTERN = "[a-zA-Z0-9\\.\\-\\:]+";
  private static final String DATASET_PATTERN = "[a-zA-Z_][a-zA-Z0-9\\_]+";
  private static final String TABLE_PATTERN = "[a-zA-Z0-9\\_]+";

  private static final String BQ_TABLE_WITH_DATASET_PATTERN =
      String.format(
          "^(?<%s>%s)\\.(?<%s>%s)$", DATASET_ID_TAG, DATASET_PATTERN, TABLE_ID_TAG, TABLE_PATTERN);

  private static final String BQ_FULL_TABLE_PATTERN =
      String.format(
          "^(?<%s>%s)[:\\.](?<%s>%s)\\.(?<%s>%s)$",
          PROJECT_ID_TAG,
          PROJECT_PATTERN,
          DATASET_ID_TAG,
          DATASET_PATTERN,
          TABLE_ID_TAG,
          TABLE_PATTERN);

  private BigQueryTableParser() {}

  // Parses a table ID with the format "project.dataset.table"
  // and returns the TableReference.
  private static Try<TableReference> parseFullTableId(String tableId) {
    Matcher matcher = Pattern.compile(BQ_FULL_TABLE_PATTERN).matcher(tableId);

    if (!matcher.find()) {
      return Try.failure(new InvalidTableReference(tableId));
    }

    TableReference tableReference = new TableReference();
    tableReference.setProjectId(matcher.group(PROJECT_ID_TAG));
    tableReference.setDatasetId(matcher.group(DATASET_ID_TAG));
    tableReference.setTableId(matcher.group(TABLE_ID_TAG));
    return Try.success(
        buildTableReference(
            matcher.group(PROJECT_ID_TAG),
            matcher.group(DATASET_ID_TAG),
            matcher.group(TABLE_ID_TAG)));
  }

  // Parses a table ID with the format "dataset.table", the project needs to be provided.
  // Returns the TableReference.
  private static Try<TableReference> parseTableIdWithoutProject(String projectId, String tableId) {
    Matcher matcher = Pattern.compile(BQ_TABLE_WITH_DATASET_PATTERN).matcher(tableId);

    if (!matcher.find()) {
      return Try.failure(new InvalidTableReference(tableId));
    }

    return Try.success(
        buildTableReference(projectId, matcher.group(DATASET_ID_TAG), matcher.group(TABLE_ID_TAG)));
  }

  // Creates a TableReference from a table ID with the format
  // "project.dataset.table".
  public static Try<TableReference> fromTableId(String tableId) {
    return BigQueryTableParser.parseFullTableId(tableId);
  }

  // Creates a TableReference from a table ID. Supports both the format
  // "project.dataset.table" and "dataset.table". In the second case,
  // the project is assigned to the provided project.
  public static Try<TableReference> fromTableId(String projectId, String tableId) {
    // Parses a table ID
    // Supports both the "project.dataset.table" format or the "dataset.table" format
    return BigQueryTableParser.fromTableId(tableId)
        .recoverWith(
            InvalidTableReference.class,
            err -> BigQueryTableParser.parseTableIdWithoutProject(projectId, tableId));
  }

  // Creates a TableReference from a BigQuery API table object
  public static TableReference fromTable(Table table) {
    TableId tableId = table.getTableId();
    TableReference tableRef =
        buildTableReference(tableId.getProject(), tableId.getDataset(), tableId.getTable());
    return tableRef;
  }

  private static boolean matchesPattern(String s, String pattern) {
    return Pattern.compile(pattern).matcher(s).matches();
  }

  // Tells whether a table ID is valid
  public static boolean isValidTableId(String tableId) {
    return matchesPattern(tableId, BQ_TABLE_WITH_DATASET_PATTERN)
        || matchesPattern(tableId, BQ_FULL_TABLE_PATTERN);
  }

  // Creates a TableReference based of projectId, datasetId and tableId
  public static TableReference buildTableReference(
      String projectId, String datasetId, String tableId) {
    TableReference tableReference = new TableReference();
    tableReference.setProjectId(projectId);
    tableReference.setDatasetId(datasetId);
    tableReference.setTableId(tableId);
    return tableReference;
  }

  public static String getStdTablePathFromTableRef(TableReference tableReference) {
    return String.format(
        "%s.%s.%s",
        tableReference.getProjectId(), tableReference.getDatasetId(), tableReference.getTableId());
  }
}

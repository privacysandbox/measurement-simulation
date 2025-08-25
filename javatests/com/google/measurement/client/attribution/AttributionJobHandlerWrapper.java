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

package com.google.measurement.client.attribution;

import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.spy;

import com.google.measurement.client.Flags;
import com.google.measurement.client.XnaSourceCreator;
import com.google.measurement.client.actions.UriConfig;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.noising.SourceNoiseHandler;
import com.google.measurement.client.reporting.AggregateDebugReportApi;
import com.google.measurement.client.reporting.DebugReportApi;
import com.google.measurement.client.reporting.EventReportWindowCalcDelegate;
import com.google.measurement.client.stats.AdServicesLogger;
import java.util.List;
import org.mockito.Mockito;

/** A wrapper class to expose a constructor for AttributionJobHandler in testing. */
public class AttributionJobHandlerWrapper {
  private final AttributionJobHandler mAttributionJobHandler;

  public AttributionJobHandlerWrapper(
      DatastoreManager datastoreManager,
      Flags flags,
      DebugReportApi debugReportApi,
      EventReportWindowCalcDelegate eventReportWindowCalcDelegate,
      SourceNoiseHandler sourceNoiseHandler,
      AdServicesLogger logger,
      AggregateDebugReportApi adrApi) {
    this.mAttributionJobHandler =
        spy(
            new AttributionJobHandler(
                datastoreManager,
                flags,
                debugReportApi,
                eventReportWindowCalcDelegate,
                sourceNoiseHandler,
                logger,
                new XnaSourceCreator(flags),
                adrApi));
  }

  public AttributionJobHandlerWrapper(AttributionJobHandler attributionJobHandler) {
    this.mAttributionJobHandler = attributionJobHandler;
  }

  /** Perform attribution. */
  public boolean performPendingAttributions() {
    return AttributionJobHandler.ProcessingResult.SUCCESS_ALL_RECORDS_PROCESSED
        == mAttributionJobHandler.performPendingAttributions();
  }

  /** Prepare noising related to aggregate reports. */
  public void prepareAggregateReportNoising(UriConfig uriConfig) {
    List<Long> nullAggregatableReportsDays = uriConfig.getNullAggregatableReportsDays();
    if (nullAggregatableReportsDays == null) {
      return;
    }
    if (nullAggregatableReportsDays.contains(0L)) {
      Mockito.doReturn(-1.0D).when(mAttributionJobHandler).getRandom();
    }
    Mockito.doReturn(nullAggregatableReportsDays)
        .when(mAttributionJobHandler)
        .getNullAggregatableReportsDays(anyLong(), anyFloat());
  }
}

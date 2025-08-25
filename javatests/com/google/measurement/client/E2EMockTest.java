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

package com.google.measurement.client;

import com.google.measurement.client.actions.Action;
import com.google.measurement.client.actions.ReportObjects;
import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import org.json.JSONException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * End-to-end test from source and trigger registration to attribution reporting, using mocked HTTP
 * requests.
 */
@RunWith(Parameterized.class)
public class E2EMockTest extends E2EAbstractMockTest {
  // private static final String TEST_DIR_NAME = "e2e_tests/test";

  private static final String TEST_DIR_NAME = "e2e_tests/msmt_e2e_tests";

  @Parameterized.Parameters(name = "{3}")
  public static Collection<Object[]> getData() throws IOException, JSONException {
    return data(TEST_DIR_NAME, E2EAbstractTest::preprocessTestJson);
  }

  public E2EMockTest(
      Collection<Action> actions,
      ReportObjects expectedOutput,
      ParamsProvider paramsProvider,
      String name,
      Map<String, String> phFlagsMap)
      throws RemoteException, IOException {
    super(actions, expectedOutput, paramsProvider, name, phFlagsMap);
    mAttributionHelper = TestObjectProvider.getAttributionJobHandler(mDatastoreManager, mFlags);
    mMeasurementImpl =
        TestObjectProvider.getMeasurementImpl(
            mDatastoreManager, mClickVerifier, mMeasurementDataDeleter, mMockContentResolver);

    mAsyncRegistrationQueueRunner =
        TestObjectProvider.getAsyncRegistrationQueueRunner(
            mSourceNoiseHandler,
            mDatastoreManager,
            mAsyncSourceFetcher,
            mAsyncTriggerFetcher,
            mDebugReportApi,
            mAggregateDebugReportApi,
            mFlags);
  }
}

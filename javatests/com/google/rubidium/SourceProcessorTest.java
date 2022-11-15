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

package com.google.rubidium;

import com.google.protobuf.ListValue;
import com.google.protobuf.Value;
import com.google.rubidium.Source.AttributionMode;
import com.google.rubidium.Source.SourceType;
import com.google.rubidium.Source.Status;
import com.google.rubidium.aggregation.AggregatableAttributionSource;
import com.google.rubidium.aggregation.AggregateFilterData;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.junit.Assert;
import org.junit.Test;

public class SourceProcessorTest {

  @Test
  public void testBuildingFromJson() throws Exception {
    String json =
        "{\"user_id\": \"U1\", \"source_event_id\": 1, \"source_type\": \"EVENT\", \"publisher\":"
            + " \"https://www.example1.com/s1\", \"web_destination\":"
            + " \"https://www.example2.com/d1\", \"enrollment_id\":"
            + " \"https://www.example3.com/r1\", \"event_time\": 1642218050000, \"expiry\":"
            + " 1647645724, \"priority\": 100, \"registrant\": \"https://www.example3.com/e1\","
            + " \"dedup_keys\": [], \"attributionMode\": \"TRUTHFULLY\","
            + " \"install_attribution_window\": 1728000, \"post_install_exclusivity_window\": 101,"
            + " \"filter_data\": {\"type\":  [\"1\"], \"ctid\":  [\"id\"]}, \"aggregation_keys\":"
            + " [{\"id\": \"myId\", \"key_piece\": \"0x1\" }]}\n";

    JSONParser parser = new JSONParser();
    Object obj = parser.parse(json);
    JSONObject jsonObject = (JSONObject) obj;
    Source source = SourceProcessor.buildSourceFromJson(jsonObject);
    assertSource(source);
  }

  @Test
  public void testBuildingFromProto() throws Exception {
    ListValue listValueId =
        ListValue.newBuilder().addValues(Value.newBuilder().setStringValue("id").build()).build();

    ListValue listValueType =
        ListValue.newBuilder().addValues(Value.newBuilder().setStringValue("1").build()).build();

    Map<String, ListValue> attributionFilterMap =
        new HashMap<String, ListValue>() {
          {
            put("ctid", listValueId);
            put("type", listValueType);
          }
        };

    Map<String, String> aggSources =
        new HashMap() {
          {
            put("key_piece", "0x1");
            put("id", "myId");
          }
        };

    InputData.AggregationKey aggregationKey =
        InputData.AggregationKey.newBuilder().putAllAggregationKeyMap(aggSources).build();

    InputData.AttributionSource protoSource =
        InputData.AttributionSource.newBuilder()
            .setSourceEventId(1)
            .setSourceType(InputData.SourceType.EVENT)
            .setPublisher("https://www.example1.com/s1")
            .setWebDestination("https://example2.com")
            .setEnrollmentId("https://www.example3.com/r1")
            .setRegistrant("https://www.example3.com/e1")
            .setEventTime(1642218050000L)
            .setExpiry(1644810050L)
            .setPriority(100)
            .setInstallAttributionWindow(1728000)
            .setPostInstallExclusivityWindow(101)
            .putAllFilterData(attributionFilterMap)
            .addAggregationKeys(aggregationKey)
            .build();

    Source source = SourceProcessor.buildSourceFromProto(protoSource);
    assertSource(source);
  }

  private void assertSource(Source source) {
    AggregateFilterData aggFilterData =
        new AggregateFilterData.Builder()
            .setAttributionFilterMap(
                new HashMap<String, List<String>>() {
                  {
                    put("ctid", Arrays.asList("id"));
                    put("type", Arrays.asList("1"));
                  }
                })
            .build();

    AggregatableAttributionSource aggAttributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(
                new HashMap<String, BigInteger>() {
                  {
                    put("myId1", new BigInteger("1"));
                  }
                })
            .setAggregateFilterData(aggFilterData)
            .build();

    Assert.assertEquals(source.getEventId(), 1);
    Assert.assertEquals(source.getSourceType(), SourceType.EVENT);
    Assert.assertEquals(source.getPublisher().toString(), "https://www.example1.com/s1");
    Assert.assertEquals(source.getWebDestination().toString(), "https://example2.com");
    Assert.assertEquals(source.getEnrollmentId(), "https://www.example3.com/r1");
    Assert.assertEquals(source.getRegistrant().toString(), "https://www.example3.com/e1");
    Assert.assertEquals(source.getStatus(), Status.ACTIVE);
    Assert.assertEquals(source.getEventTime(), 1642218050000L);
    Assert.assertEquals(source.getExpiryTime(), 1644810050000L);
    Assert.assertEquals(source.getPriority(), 100);
    Assert.assertEquals(source.getAttributionMode(), AttributionMode.TRUTHFULLY);
    Assert.assertEquals(source.getInstallAttributionWindow(), 1728000000L);
    Assert.assertEquals(source.getInstallCooldownWindow(), 101000);
    Assert.assertEquals(source.getAggregateFilterData(), "{\"ctid\":[\"id\"],\"type\":[\"1\"]}");
    Assert.assertEquals(source.getAggregateSource(), "[{\"key_piece\":\"0x1\",\"id\":\"myId\"}]");
  }
}

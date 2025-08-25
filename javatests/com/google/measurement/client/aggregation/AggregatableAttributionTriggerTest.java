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

package com.google.measurement.client.aggregation;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.measurement.client.Flags;
import com.google.measurement.client.FilterMap;
import com.google.measurement.client.util.UnsignedLong;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

/** Unit tests for {@link AggregatableAttributionTrigger} */
@RunWith(MockitoJUnitRunner.Silent.class)
public final class AggregatableAttributionTriggerTest {
  @Mock Flags mFlags;

  private List<AggregateTriggerData> createAggregateTriggerData() {
    AggregateTriggerData attributionTriggerData1 =
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(159L))
            .setSourceKeys(new HashSet<>(Arrays.asList("campCounts", "campGeoCounts")))
            .build();
    AggregateTriggerData attributionTriggerData2 =
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(5L))
            .setSourceKeys(
                new HashSet<>(Arrays.asList("campCounts", "campGeoCounts", "campGeoValue")))
            .build();
    return List.of(attributionTriggerData1, attributionTriggerData2);
  }

  private AggregatableAttributionTrigger createExampleWithValues(
      List<AggregateDeduplicationKey> aggregateDeduplicationKeys) {
    List<AggregateTriggerData> aggregateTriggerDataList = createAggregateTriggerData();
    AggregateTriggerData attributionTriggerData1 = aggregateTriggerDataList.get(0);
    AggregateTriggerData attributionTriggerData2 = aggregateTriggerDataList.get(1);
    Map<String, AggregatableKeyValue> values = new HashMap<>();
    values.put("campCounts", new AggregatableKeyValue.Builder(1).build());
    values.put("campGeoCounts", new AggregatableKeyValue.Builder(100).build());
    List<AggregatableValuesConfig> configList = new ArrayList<>();
    configList.add(new AggregatableValuesConfig.Builder(values).build());
    if (aggregateDeduplicationKeys != null) {
      return new AggregatableAttributionTrigger.Builder()
          .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
          .setValueConfigs(configList)
          .setAggregateDeduplicationKeys(aggregateDeduplicationKeys)
          .build();
    }
    return new AggregatableAttributionTrigger.Builder()
        .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
        .setValueConfigs(configList)
        .build();
  }

  private AggregatableAttributionTrigger createExampleWithValueConfigs() throws Exception {
    when(mFlags.getMeasurementEnableAggregateValueFilters()).thenReturn(true);
    List<AggregateTriggerData> aggregateTriggerDataList = createAggregateTriggerData();
    AggregateTriggerData attributionTriggerData1 = aggregateTriggerDataList.get(0);
    AggregateTriggerData attributionTriggerData2 = aggregateTriggerDataList.get(1);
    // Build AggregatableValuesConfig
    JSONObject jsonObj1Values = new JSONObject();
    jsonObj1Values.put("campCounts", 1);
    jsonObj1Values.put("campGeoCounts", 100);
    // Build filter_set and not_filter_set
    JSONObject filterMap = new JSONObject();
    filterMap.put("conversion_subdomain", new JSONArray(Arrays.asList("electronics.megastore")));
    filterMap.put("product", new JSONArray(Arrays.asList("1234", "2345")));
    JSONArray filterSet = new JSONArray();
    filterSet.put(filterMap);
    // Put into json object
    JSONObject jsonObj = new JSONObject();
    jsonObj.put("values", jsonObj1Values);
    jsonObj.put("filter_set", filterMap);
    jsonObj.put("not_filter_set", filterMap);
    AggregatableValuesConfig aggregatableValuesConfig =
        new AggregatableValuesConfig.Builder(jsonObj, mFlags).build();
    List<AggregatableValuesConfig> aggregatableValuesConfigList = new ArrayList<>();
    aggregatableValuesConfigList.add(aggregatableValuesConfig);
    return new AggregatableAttributionTrigger.Builder()
        .setTriggerData(Arrays.asList(attributionTriggerData1, attributionTriggerData2))
        .setValueConfigs(aggregatableValuesConfigList)
        .build();
  }

  @Test
  public void testCreationWithValues() throws Exception {
    AggregatableAttributionTrigger attributionTrigger = createExampleWithValues(null);

    assertThat(attributionTrigger.getTriggerData().size()).isEqualTo(2);
    assertThat(attributionTrigger.getTriggerData().get(0).getKey().longValue()).isEqualTo(159L);
    assertThat(attributionTrigger.getTriggerData().get(0).getSourceKeys().size()).isEqualTo(2);
    assertThat(attributionTrigger.getTriggerData().get(1).getKey().longValue()).isEqualTo(5L);
    assertThat(attributionTrigger.getTriggerData().get(1).getSourceKeys().size()).isEqualTo(3);
    assertThat(attributionTrigger.getValueConfigs().get(0).getValues().get("campCounts").getValue())
        .isEqualTo(1);
    assertThat(
            attributionTrigger.getValueConfigs().get(0).getValues().get("campGeoCounts").getValue())
        .isEqualTo(100);
  }

  @Test
  public void testCreationWithValueConfigs() throws Exception {
    AggregatableAttributionTrigger attributionTrigger = createExampleWithValueConfigs();
    assertThat(attributionTrigger.getTriggerData().size()).isEqualTo(2);
    assertThat(attributionTrigger.getTriggerData().get(0).getKey().longValue()).isEqualTo(159L);
    assertThat(attributionTrigger.getTriggerData().get(0).getSourceKeys().size()).isEqualTo(2);
    assertThat(attributionTrigger.getTriggerData().get(1).getKey().longValue()).isEqualTo(5L);
    assertThat(attributionTrigger.getTriggerData().get(1).getSourceKeys().size()).isEqualTo(3);
    assertThat(attributionTrigger.getValueConfigs().get(0).getValues().get("campCounts").getValue())
        .isEqualTo(1);
    assertThat(
            attributionTrigger.getValueConfigs().get(0).getValues().get("campGeoCounts").getValue())
        .isEqualTo(100);
  }

  @Test
  public void testDefaults() throws Exception {
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder().build();
    assertEquals(attributionTrigger.getTriggerData().size(), 0);
  }

  @Test
  public void testHashCode_equals() throws Exception {
    final AggregatableAttributionTrigger attributionTrigger1 = createExampleWithValues(null);
    final AggregatableAttributionTrigger attributionTrigger2 = createExampleWithValues(null);
    final Set<AggregatableAttributionTrigger> attributionTriggerSet1 = Set.of(attributionTrigger1);
    final Set<AggregatableAttributionTrigger> attributionTriggerSet2 = Set.of(attributionTrigger2);
    assertEquals(attributionTrigger1.hashCode(), attributionTrigger2.hashCode());
    assertEquals(attributionTrigger1, attributionTrigger2);
    assertEquals(attributionTriggerSet1, attributionTriggerSet2);
  }

  @Test
  public void testHashCode_notEquals() throws Exception {
    final AggregatableAttributionTrigger attributionTrigger1 = createExampleWithValues(null);

    AggregateTriggerData attributionTriggerData1 =
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(159L))
            .setSourceKeys(new HashSet<>(Arrays.asList("campCounts", "campGeoCounts")))
            .build();
    Map<String, AggregatableKeyValue> values = new HashMap<>();
    values.put("campCounts", new AggregatableKeyValue.Builder(1).build());
    values.put("campGeoCounts", new AggregatableKeyValue.Builder(100).build());

    final AggregatableAttributionTrigger attributionTrigger2 =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(Arrays.asList(attributionTriggerData1))
            .setValueConfigs(List.of(new AggregatableValuesConfig.Builder(values).build()))
            .build();
    final Set<AggregatableAttributionTrigger> attributionTriggerSet1 = Set.of(attributionTrigger1);
    final Set<AggregatableAttributionTrigger> attributionTriggerSet2 = Set.of(attributionTrigger2);
    assertNotEquals(attributionTrigger1.hashCode(), attributionTrigger2.hashCode());
    assertNotEquals(attributionTrigger1, attributionTrigger2);
    assertNotEquals(attributionTriggerSet1, attributionTriggerSet2);
  }

  @Test
  public void testExtractDedupKey_bothKeysHaveMatchingFilters() {
    Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
    triggerFilterMap1.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(10L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build()))
            .build();
    Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
    triggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(11L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build()))
            .build();

    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    FilterMap sourceFilter =
        new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isPresent());
    assertEquals(aggregateDeduplicationKey1, aggregateDeduplicationKey.get());
  }

  @Test
  public void testExtractDedupKey_secondKeyMatches_firstKeyHasInvalidFilters() {
    Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
    triggerFilterMap1.put(
        "conversion_subdomain", Collections.singletonList("electronics.ministore"));
    triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
    Map<String, List<String>> notTriggerFilterMap1 = new HashMap<>();
    notTriggerFilterMap1.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    notTriggerFilterMap1.put("product", Arrays.asList("856", "23"));

    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(10L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build()))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(notTriggerFilterMap1).build()))
            .build();

    Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
    triggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
    Map<String, List<String>> notTriggerFilterMap2 = new HashMap<>();
    notTriggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.ministore"));
    notTriggerFilterMap2.put("product", Arrays.asList("856", "23"));
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(11L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build()))
            .setNotFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(notTriggerFilterMap2).build()))
            .build();

    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    FilterMap sourceFilter =
        new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isPresent());
    assertEquals(aggregateDeduplicationKey2, aggregateDeduplicationKey.get());
  }

  @Test
  public void testExtractDedupKey_secondKeyMatches_firstKeyHasInvalidNotFilters() {
    Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
    triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
    Map<String, List<String>> notTriggerFilterMap1 = new HashMap<>();
    notTriggerFilterMap1.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(10L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build()))
            .setNotFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(notTriggerFilterMap1).build()))
            .build();

    Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
    triggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
    Map<String, List<String>> notTriggerFilterMap2 = new HashMap<>();
    notTriggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.ministore"));
    notTriggerFilterMap2.put("product", Arrays.asList("856", "23"));
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(11L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build()))
            .setNotFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(notTriggerFilterMap2).build()))
            .build();

    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    FilterMap sourceFilter =
        new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isPresent());
    assertEquals(aggregateDeduplicationKey2, aggregateDeduplicationKey.get());
  }

  @Test
  public void testExtractDedupKey_noFiltersInFirstKey() {
    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder().setDeduplicationKey(new UnsignedLong(10L)).build();
    Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
    triggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(11L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build()))
            .build();

    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    FilterMap sourceFilter =
        new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isPresent());
    assertEquals(aggregateDeduplicationKey1, aggregateDeduplicationKey.get());
  }

  @Test
  public void testExtractDedupKey_noKeysMatch() {
    Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
    triggerFilterMap1.put(
        "conversion_subdomain", Collections.singletonList("electronics.ministore"));
    triggerFilterMap1.put("product", Arrays.asList("4321", "432"));
    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(10L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build()))
            .build();
    Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
    triggerFilterMap2.put("conversion_subdomain", Collections.singletonList("electronics.store"));
    triggerFilterMap2.put("product", Arrays.asList("9876", "654"));
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(11L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build()))
            .build();

    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    FilterMap sourceFilter =
        new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isEmpty());
  }

  @Test
  public void testExtractDedupKey_secondKeyMatches_nullDedupKey() {
    Map<String, List<String>> triggerFilterMap1 = new HashMap<>();
    triggerFilterMap1.put("product", Arrays.asList("1234", "234"));
    Map<String, List<String>> notTriggerFilterMap1 = new HashMap<>();
    notTriggerFilterMap1.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder()
            .setDeduplicationKey(new UnsignedLong(10L))
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap1).build()))
            .setNotFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(notTriggerFilterMap1).build()))
            .build();

    Map<String, List<String>> triggerFilterMap2 = new HashMap<>();
    triggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.megastore"));
    triggerFilterMap2.put("product", Arrays.asList("1234", "234"));
    Map<String, List<String>> notTriggerFilterMap2 = new HashMap<>();
    notTriggerFilterMap2.put(
        "conversion_subdomain", Collections.singletonList("electronics.ministore"));
    notTriggerFilterMap2.put("product", Arrays.asList("856", "23"));
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(triggerFilterMap2).build()))
            .setNotFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder().setAttributionFilterMap(notTriggerFilterMap2).build()))
            .build();

    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    FilterMap sourceFilter =
        new FilterMap.Builder().setAttributionFilterMap(sourceFilterMap).build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isEmpty());
  }

  @Test
  public void testExtractDedupKey_lookbackWindowEnabledAndEmptyDedupKeys_returnsEmpty() {
    when(mFlags.getMeasurementEnableLookbackWindowFilter()).thenReturn(true);
    AggregateDeduplicationKey aggregateDeduplicationKey1 =
        new AggregateDeduplicationKey.Builder()
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder()
                        .addStringListValue(
                            "conversion_subdomain", List.of("electronics.megastore"))
                        .addStringListValue("product", List.of("1234", "234"))
                        .build()))
            .build();
    AggregateDeduplicationKey aggregateDeduplicationKey2 =
        new AggregateDeduplicationKey.Builder()
            .setFilterSet(
                Collections.singletonList(
                    new FilterMap.Builder()
                        .addStringListValue("conversion_subdomain", List.of("electronics.store"))
                        .addStringListValue("product", List.of("9876", "654"))
                        .build()))
            .build();

    FilterMap sourceFilter =
        new FilterMap.Builder()
            .addStringListValue("conversion_subdomain", List.of("electronics.megastore"))
            .addStringListValue("product", List.of("1234", "234"))
            .build();

    Optional<AggregateDeduplicationKey> aggregateDeduplicationKey =
        createExampleWithValues(
                Arrays.asList(aggregateDeduplicationKey1, aggregateDeduplicationKey2))
            .maybeExtractDedupKey(sourceFilter, mFlags);
    assertTrue(aggregateDeduplicationKey.isEmpty());
  }
}

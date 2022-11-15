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

package com.google.rubidium.aggregation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.Test;

/** Unit tests for {@link AggregatePayloadGenerator} */
public final class AggregatePayloadGeneratorTest {
  @Test
  public void testGenerateAttributionReportTwoContributionsSuccessfully() {
    // Build AggregatableAttributionSource.
    Map<String, BigInteger> aggregatableSource = new HashMap<>();
    aggregatableSource.put("campaignCounts", BigInteger.valueOf(345L));
    aggregatableSource.put("geoValue", BigInteger.valueOf(5L));
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .setAggregateFilterData(sourceFilter)
            .build();
    // Build AggregatableAttributionTrigger.
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    // Apply this key_piece to "campaignCounts".
    Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
    triggerDataFilter1.put("product", Collections.singletonList("1234"));
    triggerDataFilter1.put("ctid", Collections.singletonList("id"));
    Map<String, List<String>> triggerDataNotFilter1 = new HashMap<>();
    triggerDataNotFilter1.put("product", Collections.singletonList("100"));
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(1024L))
            .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
            .setFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataFilter1)
                    .build())
            .setNotFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataNotFilter1)
                    .build())
            .build());
    // Apply this key_piece to "geoValue".
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(2688L))
            .setSourceKeys(new HashSet<>(Arrays.asList("geoValue", "nonMatch")))
            .build());
    Map<String, Integer> values = new HashMap<>();
    values.put("campaignCounts", 32768);
    values.put("geoValue", 1664);
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(values)
            .build();
    Optional<List<AggregateHistogramContribution>> aggregateHistogramContributions =
        AggregatePayloadGenerator.generateAttributionReport(attributionSource, attributionTrigger);
    assertTrue(aggregateHistogramContributions.isPresent());
    List<AggregateHistogramContribution> contributions = aggregateHistogramContributions.get();
    assertEquals(contributions.size(), 2);
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(1369L))
                .setValue(32768)
                .build()));
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(2693L))
                .setValue(1664)
                .build()));
  }

  @Test
  public void testGenerateAttributionReportOnlyTwoContributionsSuccessfully() {
    // Build AggregatableAttributionSource.
    Map<String, BigInteger> aggregatableSource = new HashMap<>();
    aggregatableSource.put("campaignCounts", BigInteger.valueOf(345L));
    aggregatableSource.put("geoValue", BigInteger.valueOf(5L));
    aggregatableSource.put("thirdSource", BigInteger.valueOf(100L));
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .setAggregateFilterData(sourceFilter)
            .build();
    // Build AggregatableAttributionTrigger.
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    // Apply this key_piece to "campaignCounts".
    Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
    triggerDataFilter1.put("product", Collections.singletonList("1234"));
    triggerDataFilter1.put("ctid", Collections.singletonList("id"));
    Map<String, List<String>> triggerDataNotFilter1 = new HashMap<>();
    triggerDataNotFilter1.put("product", Collections.singletonList("100"));
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(1024L))
            .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
            .setFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataFilter1)
                    .build())
            .setNotFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataNotFilter1)
                    .build())
            .build());
    // Apply this key_piece to "geoValue".
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(2688L))
            .setSourceKeys(new HashSet<>(Arrays.asList("geoValue", "nonMatch")))
            .build());
    Map<String, Integer> values = new HashMap<>();
    values.put("campaignCounts", 32768);
    values.put("geoValue", 1664);
    values.put("thirdSource", 100);
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(values)
            .build();
    Optional<List<AggregateHistogramContribution>> aggregateHistogramContributions =
        AggregatePayloadGenerator.generateAttributionReport(attributionSource, attributionTrigger);
    assertTrue(aggregateHistogramContributions.isPresent());
    List<AggregateHistogramContribution> contributions = aggregateHistogramContributions.get();
    assertEquals(contributions.size(), 2);
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(1369L))
                .setValue(32768)
                .build()));
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(2693L))
                .setValue(1664)
                .build()));
  }

  @Test
  public void testGenerateAttributionReportMoreTriggerDataSuccessfully() {
    // Build AggregatableAttributionSource.
    Map<String, BigInteger> aggregatableSource = new HashMap<>();
    aggregatableSource.put("campaignCounts", BigInteger.valueOf(345L));
    aggregatableSource.put("geoValue", BigInteger.valueOf(5L));
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    sourceFilterMap.put("ctid", Collections.singletonList("id"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .setAggregateFilterData(sourceFilter)
            .build();
    // Build AggregatableAttributionTrigger.
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    // Apply this key_piece to "campaignCounts".
    Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
    triggerDataFilter1.put("product", Collections.singletonList("1234"));
    triggerDataFilter1.put("ctid", Collections.singletonList("id"));
    Map<String, List<String>> triggerDataNotFilter1 = new HashMap<>();
    triggerDataNotFilter1.put("product", Collections.singletonList("100"));
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(1024L))
            .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
            .setFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataFilter1)
                    .build())
            .setNotFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataNotFilter1)
                    .build())
            .build());
    // Apply this key_piece to "geoValue".
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(2688L))
            .setSourceKeys(new HashSet<>(Arrays.asList("geoValue", "nonMatch")))
            .build());
    // Apply this key_piece to "geoValue".
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(768L))
            .setSourceKeys(new HashSet<>(Collections.singletonList("geoValue")))
            .build());
    // Don't apply this key_piece.
    Map<String, List<String>> triggerDataFilter2 = new HashMap<>();
    triggerDataFilter2.put("product", Collections.singletonList("0"));
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(200L))
            .setSourceKeys(new HashSet<>(Arrays.asList("campaignCounts", "geoValue")))
            .setFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataFilter2)
                    .build())
            .build());
    Map<String, Integer> values = new HashMap<>();
    values.put("campaignCounts", 32768);
    values.put("geoValue", 1664);
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(values)
            .build();
    Optional<List<AggregateHistogramContribution>> aggregateHistogramContributions =
        AggregatePayloadGenerator.generateAttributionReport(attributionSource, attributionTrigger);
    assertTrue(aggregateHistogramContributions.isPresent());
    List<AggregateHistogramContribution> contributions = aggregateHistogramContributions.get();
    assertEquals(contributions.size(), 2);
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(1369L))
                .setValue(32768)
                .build()));
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(BigInteger.valueOf(2949L))
                .setValue(1664)
                .build()));
  }

  @Test
  public void testGenerateAttributionReportWithHighBits() {
    // Build AggregatableAttributionSource.
    Map<String, BigInteger> aggregatableSource = new HashMap<>();
    aggregatableSource.put("campaignCounts", BigInteger.valueOf(4L).shiftLeft(63));
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .setAggregateFilterData(sourceFilter)
            .build();
    // Build AggregatableAttributionTrigger.
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    // Apply this key_piece to "campaignCounts".
    Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
    triggerDataFilter1.put("product", Collections.singletonList("1234"));
    triggerDataFilter1.put("ctid", Collections.singletonList("id"));
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(2L).shiftLeft(63))
            .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
            .setFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataFilter1)
                    .build())
            .build());
    Map<String, Integer> values = new HashMap<>();
    values.put("campaignCounts", 32768);
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(values)
            .build();
    Optional<List<AggregateHistogramContribution>> aggregateHistogramContributions =
        AggregatePayloadGenerator.generateAttributionReport(attributionSource, attributionTrigger);
    assertTrue(aggregateHistogramContributions.isPresent());
    List<AggregateHistogramContribution> contributions = aggregateHistogramContributions.get();
    assertEquals(contributions.size(), 1);
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(new BigInteger("30000000000000000", 16))
                .setValue(32768)
                .build()));
  }

  @Test
  public void testGenerateAttributionReportBinaryOrsKeys() {
    // Build AggregatableAttributionSource.
    Map<String, BigInteger> aggregatableSource = new HashMap<>();
    aggregatableSource.put(
        "campaignCounts", BigInteger.valueOf(2L).shiftLeft(63).add(BigInteger.valueOf(2L)));
    Map<String, List<String>> sourceFilterMap = new HashMap<>();
    sourceFilterMap.put("conversion_subdomain", Collections.singletonList("electronics.megastore"));
    sourceFilterMap.put("product", Arrays.asList("1234", "234"));
    AggregateFilterData sourceFilter =
        new AggregateFilterData.Builder().setAttributionFilterMap(sourceFilterMap).build();
    AggregatableAttributionSource attributionSource =
        new AggregatableAttributionSource.Builder()
            .setAggregatableSource(aggregatableSource)
            .setAggregateFilterData(sourceFilter)
            .build();
    // Build AggregatableAttributionTrigger.
    List<AggregateTriggerData> triggerDataList = new ArrayList<>();
    // Apply this key_piece to "campaignCounts".
    Map<String, List<String>> triggerDataFilter1 = new HashMap<>();
    triggerDataFilter1.put("product", Collections.singletonList("1234"));
    triggerDataFilter1.put("ctid", Collections.singletonList("id"));
    triggerDataList.add(
        new AggregateTriggerData.Builder()
            .setKey(BigInteger.valueOf(2L).shiftLeft(63).add(BigInteger.valueOf(4L)))
            .setSourceKeys(new HashSet<>(Collections.singletonList("campaignCounts")))
            .setFilter(
                new AggregateFilterData.Builder()
                    .setAttributionFilterMap(triggerDataFilter1)
                    .build())
            .build());
    Map<String, Integer> values = new HashMap<>();
    values.put("campaignCounts", 32768);
    AggregatableAttributionTrigger attributionTrigger =
        new AggregatableAttributionTrigger.Builder()
            .setTriggerData(triggerDataList)
            .setValues(values)
            .build();
    Optional<List<AggregateHistogramContribution>> aggregateHistogramContributions =
        AggregatePayloadGenerator.generateAttributionReport(attributionSource, attributionTrigger);
    assertTrue(aggregateHistogramContributions.isPresent());
    List<AggregateHistogramContribution> contributions = aggregateHistogramContributions.get();
    assertEquals(contributions.size(), 1);
    assertTrue(
        contributions.contains(
            new AggregateHistogramContribution.Builder()
                .setKey(new BigInteger("10000000000000006", 16))
                .setValue(32768)
                .build()));
  }
}

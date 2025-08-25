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

package com.google.measurement.client.noising;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import com.google.measurement.client.Flags;
import com.google.measurement.client.PrivacyParams;
import com.google.common.math.LongMath;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;

public class CombinatoricsTest {
  @Test
  public void testGetKCombinationAtIndex() {
    // Test Case { {combinationIndex, k}, expectedOutput}
    long[][][] testCases = {
      {{0, 0}, {}},
      {{0, 1}, {0}},
      {{1, 1}, {1}},
      {{2, 1}, {2}},
      {{3, 1}, {3}},
      {{4, 1}, {4}},
      {{5, 1}, {5}},
      {{6, 1}, {6}},
      {{7, 1}, {7}},
      {{8, 1}, {8}},
      {{9, 1}, {9}},
      {{10, 1}, {10}},
      {{11, 1}, {11}},
      {{12, 1}, {12}},
      {{13, 1}, {13}},
      {{14, 1}, {14}},
      {{15, 1}, {15}},
      {{16, 1}, {16}},
      {{17, 1}, {17}},
      {{18, 1}, {18}},
      {{19, 1}, {19}},
      {{0, 2}, {1, 0}},
      {{1, 2}, {2, 0}},
      {{2, 2}, {2, 1}},
      {{3, 2}, {3, 0}},
      {{4, 2}, {3, 1}},
      {{5, 2}, {3, 2}},
      {{6, 2}, {4, 0}},
      {{7, 2}, {4, 1}},
      {{8, 2}, {4, 2}},
      {{9, 2}, {4, 3}},
      {{10, 2}, {5, 0}},
      {{11, 2}, {5, 1}},
      {{12, 2}, {5, 2}},
      {{13, 2}, {5, 3}},
      {{14, 2}, {5, 4}},
      {{15, 2}, {6, 0}},
      {{16, 2}, {6, 1}},
      {{17, 2}, {6, 2}},
      {{18, 2}, {6, 3}},
      {{19, 2}, {6, 4}},
      {{0, 3}, {2, 1, 0}},
      {{1, 3}, {3, 1, 0}},
      {{2, 3}, {3, 2, 0}},
      {{3, 3}, {3, 2, 1}},
      {{4, 3}, {4, 1, 0}},
      {{5, 3}, {4, 2, 0}},
      {{6, 3}, {4, 2, 1}},
      {{7, 3}, {4, 3, 0}},
      {{8, 3}, {4, 3, 1}},
      {{9, 3}, {4, 3, 2}},
      {{10, 3}, {5, 1, 0}},
      {{11, 3}, {5, 2, 0}},
      {{12, 3}, {5, 2, 1}},
      {{13, 3}, {5, 3, 0}},
      {{14, 3}, {5, 3, 1}},
      {{15, 3}, {5, 3, 2}},
      {{16, 3}, {5, 4, 0}},
      {{17, 3}, {5, 4, 1}},
      {{18, 3}, {5, 4, 2}},
      {{19, 3}, {5, 4, 3}},
      {{2924, 3}, {26, 25, 24}},
    };
    Arrays.stream(testCases)
        .forEach(
            (testCase) ->
                assertArrayEquals(
                    testCase[1],
                    Combinatorics.getKCombinationAtIndex(
                        /* combinationIndex= */ testCase[0][0], /* k= */ (int) testCase[0][1])));
  }

  @Test
  public void testGetKCombinationNoRepeat() {
    for (int k = 1; k < 5; k++) {
      Set<List<Long>> seenCombinations = new HashSet<>();
      for (int combinationIndex = 0; combinationIndex < 3000; combinationIndex++) {
        List<Long> combination =
            Arrays.stream(Combinatorics.getKCombinationAtIndex(combinationIndex, k))
                .boxed()
                .collect(Collectors.toList());
        assertTrue(seenCombinations.add(combination));
      }
    }
  }

  @Test
  public void testGetKCombinationMatchesDefinition() {
    for (int k = 1; k < 5; k++) {
      for (int index = 0; index < 3000; index++) {
        long[] combination = Combinatorics.getKCombinationAtIndex(index, k);
        long sum = 0;
        for (int i = 0; i < k; i++) {
          if ((int) combination[i] >= k - i) {
            sum += LongMath.binomial((int) combination[i], k - i);
          }
        }
        assertEquals(sum, (long) index);
      }
    }
  }

  @Test
  public void testGetNumberOfStarsAndBarsSequences() {
    assertEquals(
        3L, Combinatorics.getNumberOfStarsAndBarsSequences(/* numStars= */ 1, /* numBars= */ 2));
    assertEquals(
        2925L,
        Combinatorics.getNumberOfStarsAndBarsSequences(/* numStars= */ 3, /* numBars= */ 24));
  }

  @Test
  public void testGetStarIndices() {
    // Test Case: { {numStars, sequenceIndex}, expectedOutput }
    long[][][] testCases = {
      {{1L, 2L, 2L}, {2L}},
      {{3L, 24L, 23L}, {6L, 3L, 0L}},
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) ->
                assertArrayEquals(
                    testCase[1],
                    Combinatorics.getStarIndices(
                        /* numStars= */ (int) testCase[0][0],
                        /* sequenceIndex= */ testCase[0][2])));
  }

  @Test
  public void testGetBarsPrecedingEachStar() {
    // Test Case: {starIndices, expectedOutput}
    long[][][] testCases = {
      {{2L}, {2L}},
      {{6L, 3L, 0L}, {4L, 2L, 0L}}
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) ->
                assertArrayEquals(
                    testCase[1],
                    Combinatorics.getBarsPrecedingEachStar(/* starIndices= */ testCase[0])));
  }

  @Test
  public void testNumStatesArithmeticNoOverflow() {
    // Test Case: {numBucketIncrements, numTriggerData, numWindows}, {expected number of states}
    int[][][] testCases = {
      {{3, 8, 3}, {2925}},
      {{1, 1, 1}, {2}},
      {{1, 2, 3}, {7}},
      {{3, 2, 1}, {10}}
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) ->
                assertEquals(
                    testCase[1][0],
                    Combinatorics.getNumStatesArithmetic(
                        testCase[0][0], testCase[0][1], testCase[0][2])));
  }

  @Test
  public void testNumStatesFlexApi() {
    // Test Case: {numBucketIncrements, perTypeNumWindows, perTypeCap}, {expected number of
    // states}
    int[][][][] testCases = {
      {{{3}, {3, 3, 3, 3, 3, 3, 3, 3}, {3, 3, 3, 3, 3, 3, 3, 3}}, {{2925}}},
      {{{2}, {2, 2}, {2, 2}}, {{15}}},
      {{{3}, {2, 2}, {2, 2}}, {{27}}},
      {{{3}, {2, 2}, {3, 3}}, {{35}}},
      {{{3}, {4, 4}, {2, 2}}, {{125}}},
      {{{7}, {2, 2}, {3, 3}}, {{100}}},
      {{{7}, {2, 2}, {4, 5}}, {{236}}},
      {{{1000}, {2, 2}, {4, 5}}, {{315}}},
      {{{1000}, {2, 2, 2}, {4, 5, 4}}, {{4725}}},
      {{{1000}, {2, 2, 2, 2}, {4, 5, 4, 2}}, {{28350}}},
      {{{5}, {2}, {5}}, {{21}}},
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) ->
                assertEquals(
                    testCase[1][0][0],
                    Combinatorics.getNumStatesFlexApi(
                        testCase[0][0][0], testCase[0][1], testCase[0][2], Long.MAX_VALUE)));
  }

  @Test
  public void getNumStatesFlexApi_iterativeOverBound_returnsLongMaxValue() {
    assertEquals(
        Long.MAX_VALUE,
        Combinatorics.getNumStatesFlexApi(
            20,
            new int[] {5, 5, 5, 5, 5, 5, 5, 5},
            new int[] {20, 19, 18, 17, 16, 15, 14, 13},
            Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION));
  }

  @Test
  public void getNumStatesFlexApi_iterativeGreaterThanLong_throws() {
    assertThrows(
        ArithmeticException.class,
        () ->
            Combinatorics.getNumStatesFlexApi(
                20,
                new int[] {
                  5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5, 5,
                  5, 5, 5, 5, 5
                },
                new int[] {
                  20, 19, 18, 17, 16, 15, 14, 13, 20, 19, 18, 17, 16, 15, 14, 13, 20, 19, 18, 17,
                  16, 15, 14, 13, 20, 19, 18, 17, 16, 15, 14, 13
                },
                Long.MAX_VALUE - 1L));
  }

  @Test
  public void getNumStatesFlexApi_arithmeticOverBound_returnsLongMaxValue() {
    assertEquals(
        Long.MAX_VALUE,
        Combinatorics.getNumStatesFlexApi(
            20,
            new int[] {5, 5, 5, 5, 5, 5, 5, 5},
            new int[] {20, 20, 20, 20, 20, 20, 20, 20},
            Flags.MEASUREMENT_MAX_REPORT_STATES_PER_SOURCE_REGISTRATION));
  }

  @Test
  public void testFlipProbability() {
    // Test Case: {number of states}, {expected flip probability multiply 100}
    double[][] testCases = {
      {2925.0, 0.24263221679834088d},
      {3.0, 0.0002494582008677539d},
      {455.0, 0.037820279032938435d},
      {2.0, 0.0001663056055328264d},
      {1.0, 0.00008315280276d}
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) -> {
              double result =
                  100
                      * Combinatorics.getFlipProbability(
                          (int) testCase[0], Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON);
              assertEquals(testCase[1], result, PrivacyParams.NUMBER_EQUAL_THRESHOLD);
            });
  }

  @Test
  public void testFlipProbabilityWithNonDefaultEpsilon() {
    double defaultProbability =
        Combinatorics.getFlipProbability((int) 2925.0, Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON);
    double newProbability = Combinatorics.getFlipProbability((int) 2925.0, 0.89);
    assertNotEquals(defaultProbability, newProbability, 0);
  }

  @Test
  public void testInformationGain() {
    // Test Case: {number of states}, {expected flip probability multiply 100}
    double[][] testCases = {
      {2925.0, 11.461727965384876d},
      {3.0, 1.5849265115082312d},
      {455.0, 8.821556150827456d},
      {2.0, 0.9999820053790732d},
      {1.0, 0.0d}
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) -> {
              double result =
                  Combinatorics.getInformationGain(
                      (int) testCase[0],
                      Combinatorics.getFlipProbability(
                          (int) testCase[0], Flags.DEFAULT_MEASUREMENT_PRIVACY_EPSILON));
              assertEquals(testCase[1], result, PrivacyParams.NUMBER_EQUAL_THRESHOLD);
            });
  }

  @Test
  public void testGetMaxInformationGainWithAttributionScope() {
    double[][] testCases = {
      {2925.0, 1.0, 1.0, 11.51422090935813d},
      {3.0, 1.0, 1.0, 1.5849625007211563d},
      {455.0, 1.0, 1.0, 8.829722735086058d},
      {2.0, 1.0, 1.0, 1.0d},
      {1.0, 1.0, 1.0, 0.0d},
      {2925.0, 1.0, 3.0, 11.51422090935813d},
      {2925.0, 5.0, 3.0, 11.520127550324853d},
      {2925.0, 100.0, 3.0, 11.65374077870657d},
      {300000.0, 1.0, 1.0, 18.194602975157967d},
      {300000.0, 2.0, 2.0, 18.19461259309285d},
      {300000.0, 3.0, 3.0, 18.19463182877025d},
      {300000.0, 4.0, 4.0, 18.194660681805477d},
      {300000.0, 5.0, 5.0, 18.194699151621517d},
    };

    Arrays.stream(testCases)
        .forEach(
            (testCase) -> {
              double result =
                  Combinatorics.getMaxInformationGainWithAttributionScope(
                      (long) testCase[0], (long) testCase[1], (long) testCase[2]);
              assertEquals(testCase[3], result, PrivacyParams.NUMBER_EQUAL_THRESHOLD);
            });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          Combinatorics.getMaxInformationGainWithAttributionScope(0L, 2L, 3L);
        });
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          Combinatorics.getMaxInformationGainWithAttributionScope(3L, 2L, 0L);
        });
  }
}

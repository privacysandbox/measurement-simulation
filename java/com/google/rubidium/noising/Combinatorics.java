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

package com.google.rubidium.noising;

/** Combinatorics utilities used for randomization. */
public class Combinatorics {
  /**
   * Computes the binomial coefficient aka {@code n} choose {@code k}.
   * https://en.wikipedia.org/wiki/Binomial_coefficient
   *
   * @return binomial coefficient for (n choose k)
   * @throws ArithmeticException if the result overflows an int
   */
  static int getBinomialCoefficient(int n, int k) {
    if (k > n) {
      return 0;
    }
    if (k == n || n == 0) {
      return 1;
    }
    // getBinomialCoefficient(n, k) == getBinomialCoefficient(n, n - k).
    if (k > n - k) {
      k = n - k;
    }
    // (n choose k) = n (n -1) ... (n - (k - 1)) / k!
    // = mul((n + 1 - i) / i), i from 1 -> k.
    //
    // You might be surprised that this algorithm works just fine with integer
    // division (i.e. division occurs cleanly with no remainder). However, this is
    // true for a very simple reason. Imagine a value of `i` causes division with
    // remainder in the below algorithm. This immediately implies that
    // (n choose i) is fractional, which we know is not the case.
    int result = 1;
    for (int i = 1; i <= k; i++) {
      result = Math.multiplyExact(result, (n + 1 - i));
      result = result / i;
    }
    return result;
  }

  /**
   * Returns the k-combination associated with the number {@code combinationIndex}. In other words,
   * returns the combination of {@code k} integers uniquely indexed by {@code combinationIndex} in
   * the combinatorial number system. https://en.wikipedia.org/wiki/Combinatorial_number_system
   *
   * @return combinationIndex-th lexicographically smallest k-combination.
   * @throws ArithmeticException in case of int overflow
   */
  static int[] getKCombinationAtIndex(int combinationIndex, int k) {
    // Computes the combinationIndex-th lexicographically smallest k-combination.
    // https://en.wikipedia.org/wiki/Combinatorial_number_system
    //
    // A k-combination is a sequence of k non-negative integers in decreasing order.
    // a_k > a_{k-1} > ... > a_2 > a_1 >= 0.
    // k-combinations can be ordered lexicographically, with the smallest
    // k-combination being a_k=k-1, a_{k-1}=k-2, .., a_1=0. Given an index
    // combinationIndex>=0, and an order k, this method returns the
    // combinationIndex-th smallest k-combination.
    //
    // Given an index combinationIndex, the combinationIndex-th k-combination
    // is the unique set of k non-negative integers
    // a_k > a_{k-1} > ... > a_2 > a_1 >= 0
    // such that combinationIndex = \sum_{i=1}^k {a_i}\choose{i}
    //
    // We find this set via a simple greedy algorithm.
    // http://math0.wvstateu.edu/~baker/cs405/code/Combinadics.html
    int[] result = new int[k];
    if (k == 0) {
      return result;
    }
    // To find a_k, iterate candidates upwards from 0 until we've found the
    // maximum a such that (a choose k) <= combinationIndex. Let a_k = a. Use
    // the previous binomial coefficient to compute the next one. Note: possible
    // to speed this up via something other than incremental search.
    int target = combinationIndex;
    int candidate = k - 1;
    int binomialCoefficient = 0;
    int nextBinomialCoefficient = 1;
    while (nextBinomialCoefficient <= target) {
      candidate++;
      binomialCoefficient = nextBinomialCoefficient;
      // (n + 1 choose k) = (n choose k) * (n + 1) / (n + 1 - k)
      nextBinomialCoefficient = Math.multiplyExact(binomialCoefficient, (candidate + 1));
      nextBinomialCoefficient /= candidate + 1 - k;
    }
    // We know from the k-combination definition, all subsequent values will be
    // strictly decreasing. Find them all by decrementing candidate.
    // Use the previous binomial coefficient to compute the next one.
    int currentK = k;
    int currentIndex = 0;
    while (true) {
      if (binomialCoefficient <= target) {
        result[currentIndex] = candidate;
        currentIndex++;
        target -= binomialCoefficient;
        if (currentIndex == k) {
          return result;
        }
        // (n - 1 choose k - 1) = (n choose k) * k / n
        binomialCoefficient = binomialCoefficient * currentK / candidate;
        currentK--;
      } else {
        // (n - 1 choose k) = (n choose k) * (n - k) / n
        binomialCoefficient = binomialCoefficient * (candidate - currentK) / candidate;
      }
      candidate--;
    }
  }

  /**
   * Returns the number of possible sequences of "stars and bars" sequences
   * https://en.wikipedia.org/wiki/Stars_and_bars_(combinatorics), which is equivalent to (numStars
   * + numBars choose numStars).
   *
   * @param numStars number of stars
   * @param numBars number of bars
   * @return number of possible sequences
   */
  public static int getNumberOfStarsAndBarsSequences(int numStars, int numBars) {
    return getBinomialCoefficient(numStars + numBars, numStars);
  }

  /**
   * Returns an array of the indices of every star in the stars and bars sequence indexed by {@code
   * sequenceIndex}.
   *
   * @param numStars number of stars in the sequence
   * @param sequenceIndex index of the sequence
   * @return list of indices of every star in stars & bars sequence
   */
  public static int[] getStarIndices(int numStars, int sequenceIndex) {
    return getKCombinationAtIndex(sequenceIndex, numStars);
  }

  /**
   * From an array with the index of every star in a stars and bars sequence, returns an array
   * which, for every star, counts the number of bars preceding it.
   *
   * @param starIndices indices of the stars in descending order
   * @return count of bars preceding every star
   */
  public static int[] getBarsPrecedingEachStar(int[] starIndices) {
    for (int i = 0; i < starIndices.length; i++) {
      int starIndex = starIndices[i];
      // There are {@code starIndex} prior positions in the sequence, and `i` prior
      // stars, so there are {@code starIndex - i} prior bars.
      starIndices[i] = starIndex - (starIndices.length - 1 - i);
    }
    return starIndices;
  }
}

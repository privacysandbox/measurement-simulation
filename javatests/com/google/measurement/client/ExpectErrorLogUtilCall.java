/*
 * Copyright (C) 2024 Google LLC
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

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Used to specify expected {@code ErrorLogUtil.e(int, int)} calls over test methods.
 *
 * <ol>
 *   <li>To verify ErrorLogUtil.e(int, int) calls: @ExpectErrorLogUtilCall(X, Y)
 *   <li>To verify multiple same calls, use the times arg: @ExpectErrorLogUtilCall(X, Y, 5)
 *   <li>To verify different invocations, use multiple annotations.
 *   <li>See {@link SetErrorLogUtilDefaultParams} to specify default params at the class level.
 * </ol>
 *
 * <p>See {@link ExpectErrorLogUtilWithExceptionCall} for verifying {@code ErrorLogUtil.e(Throwable,
 * int, int)} calls.
 */
@Retention(RUNTIME)
@Target(METHOD)
public @interface ExpectErrorLogUtilCall {
  /** Name of annotation */
  String ANNOTATION_NAME = ExpectErrorLogUtilCall.class.getSimpleName();

  /** Internal value to represent unspecified int logging parameter. */
  int UNDEFINED_INT_PARAM = Integer.MIN_VALUE;

  /** Default number of times to expect log call. */
  int DEFAULT_TIMES = 1;

  /**
   * Error code to be logged.
   *
   * <p>It's required to define this using {@link SetErrorLogUtilDefaultParams} at the class level
   * if it is not defined within this annotation.
   */
  int errorCode() default UNDEFINED_INT_PARAM;

  /**
   * PPAPI name code to be logged.
   *
   * <p>It's required to define this using {@link SetErrorLogUtilDefaultParams} at the class level
   * if it is not defined within this annotation.
   */
  int ppapiName() default UNDEFINED_INT_PARAM;

  /** Number of log calls, default set to 1 */
  int times() default DEFAULT_TIMES;
}

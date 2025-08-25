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

import static com.google.measurement.client.ExpectErrorLogUtilCall.UNDEFINED_INT_PARAM;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

/**
 * Class level annotation that can be used to specify default expected parameters to be inherited by
 * {@link ExpectErrorLogUtilCall} / {@link ExpectErrorLogUtilWithExceptionCall} if left unspecified.
 *
 * <p>Parameter(s) specified by the annotation over the test method will always take precedence.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface SetErrorLogUtilDefaultParams {
  /** Default throwable to be used by {@link ExpectErrorLogUtilWithExceptionCall}. */
  Class<? extends Throwable> throwable() default Exception.class;

  /**
   * Default error code to be used by {@link ExpectErrorLogUtilCall} / {@link
   * ExpectErrorLogUtilWithExceptionCall}.
   */
  int errorCode() default UNDEFINED_INT_PARAM;

  /**
   * Default ppapi name code to be used by {@link ExpectErrorLogUtilCall} / {@link
   * ExpectErrorLogUtilWithExceptionCall}.
   */
  int ppapiName() default UNDEFINED_INT_PARAM;
}

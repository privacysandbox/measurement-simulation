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

package com.google.measurement.client.registration;

import com.google.measurement.client.ContentProviderClient;
import com.google.measurement.client.ContentResolver;
import com.google.measurement.client.data.DatastoreException;
import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.IMeasurementDao;
import com.google.measurement.client.LoggerFactory;
import com.google.measurement.client.NonNull;
import com.google.measurement.client.Nullable;
import com.google.measurement.client.RemoteException;
import com.google.measurement.client.Source.SourceType;
import com.google.measurement.client.Uri;
import com.google.measurement.client.WebSourceParams;
import com.google.measurement.client.WebTriggerParams;
import java.util.Objects;
import java.util.UUID;

/** Class containing static functions for enqueueing AsyncRegistrations */
public class EnqueueAsyncRegistration {

  /**
   * Inserts an App Source or Trigger Registration request into the Async Registration Queue table.
   *
   * @param registrationRequest a {@link RegistrationRequest} to be queued.
   */
  public static boolean appSourceOrTriggerRegistrationRequest(
      RegistrationRequest registrationRequest,
      boolean adIdPermission,
      @NonNull Uri registrant,
      long requestTime,
      @NonNull SourceType sourceType,
      @Nullable String postBody,
      @NonNull DatastoreManager datastoreManager,
      @NonNull ContentResolver contentResolver) {
    Objects.requireNonNull(contentResolver);
    Objects.requireNonNull(datastoreManager);
    return datastoreManager.runInTransaction(
        (dao) -> {
          if (hasValidScheme(registrationRequest.getRegistrationUri())) {
            insertAsyncRegistration(
                UUID.randomUUID().toString(),
                registrationRequest.getRegistrationUri(),
                /* mWebDestination */ null,
                /* mOsDestination */ null,
                registrant,
                /* verifiedDestination */ null,
                registrant,
                registrationRequest.getRegistrationType() == RegistrationRequest.REGISTER_SOURCE
                    ? AsyncRegistration.RegistrationType.APP_SOURCE
                    : AsyncRegistration.RegistrationType.APP_TRIGGER,
                sourceType,
                requestTime,
                false,
                adIdPermission,
                registrationRequest.getAdIdValue(),
                postBody,
                UUID.randomUUID().toString(),
                dao,
                contentResolver);
          }
        });
  }

  /**
   * Inserts a Web Source Registration request into the Async Registration Queue table.
   *
   * @param webSourceRegistrationRequest a {@link WebSourceRegistrationRequest} to be queued.
   */
  public static boolean webSourceRegistrationRequest(
      WebSourceRegistrationRequest webSourceRegistrationRequest,
      boolean adIdPermission,
      @NonNull Uri registrant,
      long requestTime,
      @Nullable SourceType sourceType,
      @NonNull DatastoreManager datastoreManager,
      @NonNull ContentResolver contentResolver) {
    Objects.requireNonNull(contentResolver);
    Objects.requireNonNull(datastoreManager);
    String registrationId = UUID.randomUUID().toString();
    return datastoreManager.runInTransaction(
        (dao) -> {
          for (WebSourceParams webSourceParams : webSourceRegistrationRequest.getSourceParams()) {
            if (hasValidScheme(webSourceParams.getRegistrationUri())) {
              insertAsyncRegistration(
                  UUID.randomUUID().toString(),
                  webSourceParams.getRegistrationUri(),
                  webSourceRegistrationRequest.getWebDestination(),
                  webSourceRegistrationRequest.getAppDestination(),
                  registrant,
                  webSourceRegistrationRequest.getVerifiedDestination(),
                  webSourceRegistrationRequest.getTopOriginUri(),
                  AsyncRegistration.RegistrationType.WEB_SOURCE,
                  sourceType,
                  requestTime,
                  webSourceParams.isDebugKeyAllowed(),
                  adIdPermission,
                  /* adIdValue */ null, // null for web
                  /* postBody */ null,
                  registrationId,
                  dao,
                  contentResolver);
            }
          }
        });
  }

  /**
   * Inserts a Web Trigger Registration request into the Async Registration Queue table.
   *
   * @param webTriggerRegistrationRequest a {@link WebTriggerRegistrationRequest} to be queued.
   */
  public static boolean webTriggerRegistrationRequest(
      WebTriggerRegistrationRequest webTriggerRegistrationRequest,
      boolean adIdPermission,
      @NonNull Uri registrant,
      long requestTime,
      @NonNull DatastoreManager datastoreManager,
      @NonNull ContentResolver contentResolver) {
    Objects.requireNonNull(contentResolver);
    Objects.requireNonNull(datastoreManager);
    String registrationId = UUID.randomUUID().toString();
    return datastoreManager.runInTransaction(
        (dao) -> {
          for (WebTriggerParams webTriggerParams :
              webTriggerRegistrationRequest.getTriggerParams()) {
            if (hasValidScheme(webTriggerParams.getRegistrationUri())) {
              insertAsyncRegistration(
                  UUID.randomUUID().toString(),
                  webTriggerParams.getRegistrationUri(),
                  /* mWebDestination */ null,
                  /* mOsDestination */ null,
                  registrant,
                  /* mVerifiedDestination */ null,
                  webTriggerRegistrationRequest.getDestination(),
                  AsyncRegistration.RegistrationType.WEB_TRIGGER,
                  /* mSourceType */ null,
                  requestTime,
                  webTriggerParams.isDebugKeyAllowed(),
                  adIdPermission,
                  /* adIdValue */ null, // null for web
                  /* postBody */ null,
                  registrationId,
                  dao,
                  contentResolver);
            }
          }
        });
  }

  /**
   * Inserts an App Sources Registration request into the Async Registration Queue table.
   *
   * @param requestInternal a {@link RegistrationRequest} to be queued.
   */
  public static boolean appSourcesRegistrationRequest(
      @NonNull SourceRegistrationRequestInternal requestInternal,
      boolean adIdPermission,
      @NonNull Uri registrant,
      long requestTime,
      @NonNull SourceType sourceType,
      @Nullable String postBody,
      @NonNull DatastoreManager datastoreManager,
      @NonNull ContentResolver contentResolver) {
    Objects.requireNonNull(contentResolver);
    Objects.requireNonNull(datastoreManager);
    String registrationId = UUID.randomUUID().toString();
    return datastoreManager.runInTransaction(
        (dao) -> {
          for (Uri registrationUri :
              requestInternal.getSourceRegistrationRequest().getRegistrationUris()) {
            if (hasValidScheme(registrationUri)) {
              insertAsyncRegistration(
                  UUID.randomUUID().toString(),
                  registrationUri,
                  /* mWebDestination */ null,
                  /* mOsDestination */ null,
                  registrant,
                  /* verifiedDestination */ null,
                  registrant,
                  AsyncRegistration.RegistrationType.APP_SOURCES,
                  sourceType,
                  requestTime,
                  false,
                  adIdPermission,
                  requestInternal.getAdIdValue(),
                  postBody,
                  registrationId,
                  dao,
                  contentResolver);
            }
          }
        });
  }

  private static void insertAsyncRegistration(
      String id,
      Uri registrationUri,
      Uri webDestination,
      Uri osDestination,
      Uri registrant,
      Uri verifiedDestination,
      Uri topOrigin,
      AsyncRegistration.RegistrationType registrationType,
      SourceType sourceType,
      long mRequestTime,
      boolean debugKeyAllowed,
      boolean adIdPermission,
      String platformAdIdValue,
      String postBody,
      String registrationId,
      IMeasurementDao dao,
      ContentResolver contentResolver)
      throws DatastoreException {
    AsyncRegistration asyncRegistration =
        new AsyncRegistration.Builder()
            .setId(id)
            .setRegistrationUri(registrationUri)
            .setWebDestination(webDestination)
            .setOsDestination(osDestination)
            .setRegistrant(registrant)
            .setVerifiedDestination(verifiedDestination)
            .setTopOrigin(topOrigin)
            .setType(registrationType)
            .setSourceType(sourceType)
            .setRequestTime(mRequestTime)
            .setRetryCount(0)
            .setDebugKeyAllowed(debugKeyAllowed)
            .setAdIdPermission(adIdPermission)
            .setPlatformAdId(platformAdIdValue)
            .setPostBody(postBody)
            .setRegistrationId(registrationId)
            .setRedirectBehavior(AsyncRedirect.RedirectBehavior.AS_IS)
            .build();

    dao.insertAsyncRegistration(asyncRegistration);
    notifyContentProvider(contentResolver);
  }

  private static void notifyContentProvider(ContentResolver contentResolver) {
    Uri triggerUri = AsyncRegistrationContentProvider.getTriggerUri();
    try (ContentProviderClient contentProviderClient =
        contentResolver.acquireContentProviderClient(triggerUri)) {
      if (contentProviderClient != null) {
        contentProviderClient.insert(triggerUri, null);
      }
    } catch (RemoteException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "AsyncRegistration Content Provider invocation failed.");
    }
  }

  private static boolean hasValidScheme(Uri registrationUri) {
    return registrationUri.getScheme().equalsIgnoreCase("https");
  }
}

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

import static com.google.measurement.client.AdServicesStatusUtils.STATUS_INTERNAL_ERROR;
import static com.google.measurement.client.AdServicesStatusUtils.STATUS_INVALID_ARGUMENT;
import static com.google.measurement.client.AdServicesStatusUtils.STATUS_IO_ERROR;
import static com.google.measurement.client.AdServicesStatusUtils.STATUS_SUCCESS;
import static com.google.measurement.client.stats.AdServicesStatsLog.AD_SERVICES_MEASUREMENT_WIPEOUT;

import com.google.measurement.client.data.DatastoreManager;
import com.google.measurement.client.data.DatastoreManagerFactory;
import com.google.measurement.client.deletion.MeasurementDataDeleter;
import com.google.common.base.Supplier;
import com.google.common.base.Suppliers;
import com.google.measurement.client.registration.EnqueueAsyncRegistration;
import com.google.measurement.client.registration.RegistrationRequest;
import com.google.measurement.client.registration.SourceRegistrationRequestInternal;
import com.google.measurement.client.registration.WebSourceRegistrationRequest;
import com.google.measurement.client.registration.WebSourceRegistrationRequestInternal;
import com.google.measurement.client.registration.WebTriggerRegistrationRequest;
import com.google.measurement.client.registration.WebTriggerRegistrationRequestInternal;
import com.google.measurement.client.stats.AdServicesLoggerImpl;
import com.google.measurement.client.stats.MeasurementWipeoutStats;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * This class is thread safe.
 *
 * @hide
 */
public final class MeasurementImpl {
  private static final String ANDROID_APP_SCHEME = "android-app";
  private static volatile MeasurementImpl sMeasurementImpl;
  private static volatile Supplier<MeasurementImpl> sMeasurementImplSupplier =
      Suppliers.memoize(() -> new MeasurementImpl(ApplicationContextSingleton.get()));
  private final Context mContext;
  private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();
  private final DatastoreManager mDatastoreManager;
  private final ContentResolver mContentResolver;
  private final ClickVerifier mClickVerifier;
  private final MeasurementDataDeleter mMeasurementDataDeleter;
  private final Flags mFlags;

  @VisibleForTesting
  MeasurementImpl(Context context) {
    mContext = context;
    mDatastoreManager = DatastoreManagerFactory.getDatastoreManager();
    mClickVerifier = new ClickVerifier(context);
    mFlags = FlagsFactory.getFlags();
    mMeasurementDataDeleter = new MeasurementDataDeleter(mDatastoreManager, mFlags);
    mContentResolver = mContext.getContentResolver();
    deleteOnRollback();
  }

  @VisibleForTesting
  public MeasurementImpl(
      Context context,
      Flags flags,
      DatastoreManager datastoreManager,
      ClickVerifier clickVerifier,
      MeasurementDataDeleter measurementDataDeleter,
      ContentResolver contentResolver) {
    mContext = context;
    mDatastoreManager = datastoreManager;
    mClickVerifier = clickVerifier;
    mMeasurementDataDeleter = measurementDataDeleter;
    mFlags = flags;
    mContentResolver = contentResolver;
  }

  /**
   * Gets an instance of MeasurementImpl to be used.
   *
   * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the existing
   * instance will be returned.
   */
  @NonNull
  public static MeasurementImpl getInstance() {
    if (sMeasurementImpl == null) {
      synchronized (MeasurementImpl.class) {
        if (sMeasurementImpl == null) {
          sMeasurementImpl = sMeasurementImplSupplier.get();
        }
      }
    }
    return sMeasurementImpl;
  }

  /** Gets an supplier of MeasurementImpl to be used. */
  @NonNull
  public static Supplier<MeasurementImpl> getSingletonSupplier() {
    return sMeasurementImplSupplier;
  }

  /**
   * Invoked when a package is installed.
   *
   * @param packageUri installed package {@link Uri}.
   * @param eventTime time when the package was installed.
   */
  public void doInstallAttribution(@NonNull Uri packageUri, long eventTime) {
    LoggerFactory.getMeasurementLogger().d("Attributing installation for: " + packageUri);
    Uri appUri = getAppUri(packageUri);
    mReadWriteLock.readLock().lock();
    try {
      mDatastoreManager.runInTransaction((dao) -> dao.doInstallAttribution(appUri, eventTime));
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /** Implement a registration request, returning a {@link AdServicesStatusUtils.StatusCode}. */
  @AdServicesStatusUtils.StatusCode
  int register(@NonNull RegistrationRequest request, boolean adIdPermission, long requestTime) {
    mReadWriteLock.readLock().lock();
    try {
      switch (request.getRegistrationType()) {
        case RegistrationRequest.REGISTER_SOURCE:
        case RegistrationRequest.REGISTER_TRIGGER:
          return EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
                  request,
                  adIdPermission,
                  getRegistrant(request.getAppPackageName()),
                  requestTime,
                  request.getRegistrationType() == RegistrationRequest.REGISTER_TRIGGER
                      ? null
                      : getSourceType(
                          request.getInputEvent(),
                          request.getRequestTime(),
                          request.getAppPackageName()),
                  /* postBody */ null,
                  mDatastoreManager,
                  mContentResolver)
              ? STATUS_SUCCESS
              : STATUS_IO_ERROR;

        default:
          return STATUS_INVALID_ARGUMENT;
      }
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /**
   * Implement a sources registration request, returning a {@link AdServicesStatusUtils.StatusCode}.
   */
  @AdServicesStatusUtils.StatusCode
  int registerSources(@NonNull SourceRegistrationRequestInternal request, long requestTime) {
    mReadWriteLock.readLock().lock();
    try {
      return EnqueueAsyncRegistration.appSourcesRegistrationRequest(
              request,
              isAdIdPermissionGranted(request.getAdIdValue()),
              getRegistrant(request.getAppPackageName()),
              requestTime,
              getSourceType(
                  request.getSourceRegistrationRequest().getInputEvent(),
                  request.getBootRelativeRequestTime(),
                  request.getAppPackageName()),
              /* postBody*/ null,
              mDatastoreManager,
              mContentResolver)
          ? STATUS_SUCCESS
          : STATUS_IO_ERROR;
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /**
   * Processes a source registration request delegated to OS from the caller, e.g. Chrome, returning
   * a status code.
   */
  int registerWebSource(
      @NonNull WebSourceRegistrationRequestInternal request,
      boolean adIdPermission,
      long requestTime) {
    WebSourceRegistrationRequest sourceRegistrationRequest = request.getSourceRegistrationRequest();
    if (!isValid(sourceRegistrationRequest)) {
      LoggerFactory.getMeasurementLogger().e("registerWebSource received invalid parameters");
      return STATUS_INVALID_ARGUMENT;
    }
    mReadWriteLock.readLock().lock();
    try {
      boolean enqueueStatus =
          EnqueueAsyncRegistration.webSourceRegistrationRequest(
              sourceRegistrationRequest,
              adIdPermission,
              getRegistrant(request.getAppPackageName()),
              requestTime,
              getSourceType(
                  sourceRegistrationRequest.getInputEvent(),
                  request.getRequestTime(),
                  request.getAppPackageName()),
              mDatastoreManager,
              mContentResolver);
      if (enqueueStatus) {
        return STATUS_SUCCESS;
      } else {

        return STATUS_IO_ERROR;
      }
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /**
   * Processes a trigger registration request delegated to OS from the caller, e.g. Chrome,
   * returning a status code.
   */
  int registerWebTrigger(
      @NonNull WebTriggerRegistrationRequestInternal request,
      boolean adIdPermission,
      long requestTime) {
    WebTriggerRegistrationRequest triggerRegistrationRequest =
        request.getTriggerRegistrationRequest();
    if (!isValid(triggerRegistrationRequest)) {
      LoggerFactory.getMeasurementLogger().e("registerWebTrigger received invalid parameters");
      return STATUS_INVALID_ARGUMENT;
    }
    mReadWriteLock.readLock().lock();
    try {
      boolean enqueueStatus =
          EnqueueAsyncRegistration.webTriggerRegistrationRequest(
              triggerRegistrationRequest,
              adIdPermission,
              getRegistrant(request.getAppPackageName()),
              requestTime,
              mDatastoreManager,
              mContentResolver);
      if (enqueueStatus) {
        return STATUS_SUCCESS;
      } else {

        return STATUS_IO_ERROR;
      }
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /** Implement a source registration request from a report event */
  public int registerEvent(
      @NonNull Uri registrationUri,
      @NonNull String appPackageName,
      @NonNull String sdkPackageName,
      boolean isAdIdEnabled,
      @Nullable String postBody,
      @Nullable InputEvent inputEvent,
      @Nullable String adIdValue) {
    Objects.requireNonNull(registrationUri);
    Objects.requireNonNull(appPackageName);
    Objects.requireNonNull(sdkPackageName);

    final long apiRequestTime = System.currentTimeMillis();
    final RegistrationRequest.Builder builder =
        new RegistrationRequest.Builder(
                RegistrationRequest.REGISTER_SOURCE,
                registrationUri,
                appPackageName,
                sdkPackageName)
            .setAdIdPermissionGranted(isAdIdEnabled)
            .setRequestTime(System.currentTimeMillis())
            .setAdIdValue(adIdValue);
    RegistrationRequest request = builder.build();

    mReadWriteLock.readLock().lock();
    try {
      return EnqueueAsyncRegistration.appSourceOrTriggerRegistrationRequest(
              request,
              request.isAdIdPermissionGranted(),
              registrationUri,
              apiRequestTime,
              getSourceType(inputEvent, request.getRequestTime(), request.getAppPackageName()),
              postBody,
              mDatastoreManager,
              mContentResolver)
          ? STATUS_SUCCESS
          : STATUS_IO_ERROR;
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /**
   * Implement a deleteRegistrations request, returning a r{@link AdServicesStatusUtils.StatusCode}.
   */
  @AdServicesStatusUtils.StatusCode
  int deleteRegistrations(@NonNull DeletionParam request) {
    mReadWriteLock.readLock().lock();
    try {
      boolean deleteResult = mMeasurementDataDeleter.delete(request);
      if (deleteResult) {
        markDeletion();
      }
      return deleteResult ? STATUS_SUCCESS : STATUS_INTERNAL_ERROR;
    } catch (NullPointerException | IllegalArgumentException e) {
      LoggerFactory.getMeasurementLogger().e(e, "Delete registration received invalid parameters");
      return STATUS_INVALID_ARGUMENT;
    } finally {
      mReadWriteLock.readLock().unlock();
    }
  }

  /**
   * Delete all records from a specific package and return a boolean value to indicate whether any
   * data was deleted.
   */
  public boolean deletePackageRecords(Uri packageUri, long eventTime) {
    Uri appUri = getAppUri(packageUri);
    LoggerFactory.getMeasurementLogger().d("Deleting records for " + appUri);
    mReadWriteLock.writeLock().lock();
    boolean didDeletionOccur = false;
    try {
      didDeletionOccur = mMeasurementDataDeleter.deleteAppUninstalledData(appUri, eventTime);
      if (didDeletionOccur) {
        markDeletion();
      }
    } catch (NullPointerException | IllegalArgumentException e) {
      LoggerFactory.getMeasurementLogger()
          .e(e, "Delete package records received invalid parameters");
    } finally {
      mReadWriteLock.writeLock().unlock();
    }
    return didDeletionOccur;
  }

  private static boolean isAdIdPermissionGranted(@Nullable String adIdValue) {
    return true;
  }

  @VisibleForTesting
  Source.SourceType getSourceType(
      InputEvent inputEvent, long requestTime, String sourceRegistrant) {
    // If click verification is enabled and the InputEvent is not null, but it cannot be
    // verified, then the SourceType is demoted to EVENT.
    if (mFlags.getMeasurementIsClickVerificationEnabled()
        && inputEvent != null
        && !mClickVerifier.isInputEventVerifiable(inputEvent, requestTime, sourceRegistrant)) {
      return Source.SourceType.EVENT;
    } else {
      return inputEvent == null ? Source.SourceType.EVENT : Source.SourceType.NAVIGATION;
    }
  }

  private Uri getRegistrant(String packageName) {
    return Uri.parse(ANDROID_APP_SCHEME + "://" + packageName);
  }

  private Uri getAppUri(Uri packageUri) {
    return packageUri.getScheme() == null
        ? Uri.parse(ANDROID_APP_SCHEME + "://" + packageUri.getEncodedSchemeSpecificPart())
        : packageUri;
  }

  private boolean isValid(WebSourceRegistrationRequest sourceRegistrationRequest) {
    Uri verifiedDestination = sourceRegistrationRequest.getVerifiedDestination();
    Uri webDestination = sourceRegistrationRequest.getWebDestination();

    if (verifiedDestination == null) {
      return webDestination == null
          ? true
          : WebAddresses.topPrivateDomainAndScheme(webDestination).isPresent();
    }

    return true;
  }

  private static boolean isValid(WebTriggerRegistrationRequest triggerRegistrationRequest) {
    Uri destination = triggerRegistrationRequest.getDestination();
    return WebAddresses.topPrivateDomainAndScheme(destination).isPresent();
  }

  /**
   * Checks if the module was rollback and if there was a deletion in the version rolled back from.
   * If there was, delete all measurement data to prioritize user privacy.
   */
  private void deleteOnRollback() {
    if (FlagsFactory.getFlags().getMeasurementRollbackDeletionKillSwitch()) {
      LoggerFactory.getMeasurementLogger()
          .e("Rollback deletion is disabled. Not checking system server for rollback.");
      return;
    }

    LoggerFactory.getMeasurementLogger().d("Checking rollback status.");
    boolean needsToHandleRollbackReconciliation = checkIfNeedsToHandleReconciliation();
    if (needsToHandleRollbackReconciliation) {
      LoggerFactory.getMeasurementLogger()
          .d("Rollback and deletion detected, deleting all measurement data.");
      mReadWriteLock.writeLock().lock();
      boolean success;
      try {
        success =
            mDatastoreManager.runInTransaction(
                (dao) -> dao.deleteAllMeasurementData(Collections.emptyList()));
      } finally {
        mReadWriteLock.writeLock().unlock();
      }
      if (success) {
        AdServicesLoggerImpl.getInstance()
            .logMeasurementWipeoutStats(
                new MeasurementWipeoutStats.Builder()
                    .setCode(AD_SERVICES_MEASUREMENT_WIPEOUT)
                    .setWipeoutType(WipeoutStatus.WipeoutType.ROLLBACK_WIPEOUT_CAUSE.getValue())
                    .setSourceRegistrant("")
                    .build());
      }
    }
  }

  @VisibleForTesting
  boolean checkIfNeedsToHandleReconciliation() {
    if (SdkLevel.isAtLeastT()) {
      return false;
    }

    // Not on Android T+. Check if flag is enabled if on S.
    if (isMeasurementRollbackCompatDisabled()) {
      LoggerFactory.getMeasurementLogger()
          .e("Rollback deletion disabled. Not checking compatible store for rollback.");
      return false;
    }

    return false;
  }

  /**
   * Stores a bit in the system server indicating that a deletion happened for the current
   * AdServices module version. This information is used for deleting data after it has been
   * restored by a module rollback.
   */
  private void markDeletion() {
    if (FlagsFactory.getFlags().getMeasurementRollbackDeletionKillSwitch()) {
      LoggerFactory.getMeasurementLogger()
          .e("Rollback deletion is disabled. Not storing status in system server.");
      return;
    }

    if (SdkLevel.isAtLeastT()) {
      LoggerFactory.getMeasurementLogger().d("Marking deletion in system server.");
      // AdServicesManager.getInstance(mContext)
      //     .recordAdServicesDeletionOccurred(AdServicesManager.MEASUREMENT_DELETION);
      return;
    }

    // If on Android S, check if the appropriate flag is enabled, otherwise do nothing.
    if (isMeasurementRollbackCompatDisabled()) {
      LoggerFactory.getMeasurementLogger()
          .e("Rollback deletion disabled. Not storing status in compatible store.");
      return;
    }

    //   MeasurementRollbackCompatManager.getInstance(mContext,
    // AdServicesManager.MEASUREMENT_DELETION)
    //       .recordAdServicesDeletionOccurred();
  }

  private boolean isMeasurementRollbackCompatDisabled() {
    if (SdkLevel.isAtLeastT()) {
      // This method should never be called on T+.
      return true;
    }

    Flags flags = FlagsFactory.getFlags();
    return !SdkLevel.isAtLeastS() || flags.getMeasurementRollbackDeletionAppSearchKillSwitch();
  }
}

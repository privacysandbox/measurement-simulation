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

package com.google.measurement;

import com.google.measurement.util.UnsignedLong;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/** Util class for DebugKeys */
public class DebugKeyAccessor {
  private final Flags mFlags;
  private final IMeasurementDAO mMeasurementDao;

  public enum AttributionType {
    SOURCE_APP_TRIGGER_APP,
    SOURCE_APP_TRIGGER_WEB,
    SOURCE_WEB_TRIGGER_APP,
    SOURCE_WEB_TRIGGER_WEB,
  }

  public DebugKeyAccessor(IMeasurementDAO measurementDao) {
    this(new Flags(), measurementDao);
  }

  DebugKeyAccessor(Flags flags, IMeasurementDAO measurementDao) {
    mFlags = flags;
    mMeasurementDao = measurementDao;
  }

  /** Returns DebugKey according to the permissions set */
  public Map<UnsignedLong, UnsignedLong> getDebugKeysForVerboseTriggerDebugReport(
      Source source, Trigger trigger) {
    HashMap<UnsignedLong, UnsignedLong> keys = new HashMap<>();
    if (source == null) {
      if (trigger.getDestinationType() == EventSurfaceType.WEB && trigger.hasArDebugPermission()) {
        keys.put(null, trigger.getDebugKey());
        return keys;
      }
    }
    UnsignedLong sourceDebugKey = null;
    UnsignedLong triggerDebugKey = null;
    Long joinKeyHash = null;
    AttributionType attributionType = getAttributionType(source, trigger);
    boolean doDebugJoinKeysMatch = false;
    switch (attributionType) {
      case SOURCE_APP_TRIGGER_APP:
        if (source.hasAdIdPermission()) {
          sourceDebugKey = source.getDebugKey();
        }
        if (trigger.hasAdIdPermission()) {
          triggerDebugKey = trigger.getDebugKey();
        }
        break;
      case SOURCE_WEB_TRIGGER_WEB:
        if (trigger.getRegistrant().equals(source.getRegistrant())) {
          if (source.hasArDebugPermission()) {
            sourceDebugKey = source.getDebugKey();
          }
          if (trigger.hasArDebugPermission()) {
            triggerDebugKey = trigger.getDebugKey();
          }
        } else {
          if (canMatchJoinKeys(source, trigger)) {
            joinKeyHash = 0L;
            if (source.getDebugJoinKey().equals(trigger.getDebugJoinKey())) {
              sourceDebugKey = source.getDebugKey();
              triggerDebugKey = trigger.getDebugKey();
              joinKeyHash = (long) source.getDebugJoinKey().hashCode();
              doDebugJoinKeysMatch = true;
            }
          }
        }
        break;
      case SOURCE_APP_TRIGGER_WEB:
        if (canMatchAdIdAppSourceToWebTrigger(source, trigger)) {
          if (source.getPlatformAdId().equals(trigger.getDebugAdId())
              && isEnrollmentIdWithinUniqueAdIdLimit(trigger.getEnrollmentId())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
          }
          break;
        }
      case SOURCE_WEB_TRIGGER_APP:
        if (canMatchAdIdWebSourceToAppTrigger(source, trigger)) {
          if (trigger.getPlatformAdId().equals(source.getDebugAdId())
              && isEnrollmentIdWithinUniqueAdIdLimit(source.getEnrollmentId())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
          }
          break;
        }
        if (canMatchJoinKeys(source, trigger)) {
          joinKeyHash = 0L;
          if (source.getDebugJoinKey().equals(trigger.getDebugJoinKey())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
            joinKeyHash = (long) source.getDebugJoinKey().hashCode();
            doDebugJoinKeysMatch = true;
          }
        }
        break;
      default:
        break;
    }
    keys.put(sourceDebugKey, triggerDebugKey);
    return keys;
  }

  /** Returns DebugKey according to the permissions set */
  public Map<UnsignedLong, UnsignedLong> getDebugKeys(Source source, Trigger trigger) {
    UnsignedLong sourceDebugKey = null;
    UnsignedLong triggerDebugKey = null;
    Long joinKeyHash = null;
    AttributionType attributionType = getAttributionType(source, trigger);
    boolean doDebugJoinKeysMatch = false;
    switch (attributionType) {
      case SOURCE_APP_TRIGGER_APP:
        if (source.hasAdIdPermission()) {
          sourceDebugKey = source.getDebugKey();
        }
        if (trigger.hasAdIdPermission()) {
          triggerDebugKey = trigger.getDebugKey();
        }
        break;
      case SOURCE_WEB_TRIGGER_WEB:
        if (trigger.getRegistrant().equals(source.getRegistrant())) {
          if (source.hasArDebugPermission()) {
            sourceDebugKey = source.getDebugKey();
          }
          if (trigger.hasArDebugPermission()) {
            triggerDebugKey = trigger.getDebugKey();
          }
        } else if (canMatchJoinKeys(source, trigger)) {
          joinKeyHash = 0L;
          if (source.getDebugJoinKey().equals(trigger.getDebugJoinKey())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
            joinKeyHash = (long) source.getDebugJoinKey().hashCode();
            doDebugJoinKeysMatch = true;
          }
        }
        break;
      case SOURCE_APP_TRIGGER_WEB:
        if (canMatchAdIdAppSourceToWebTrigger(source, trigger)) {
          if (source.getPlatformAdId().equals(trigger.getDebugAdId())
              && isEnrollmentIdWithinUniqueAdIdLimit(trigger.getEnrollmentId())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
          }
          break;
        }
      case SOURCE_WEB_TRIGGER_APP:
        if (canMatchAdIdWebSourceToAppTrigger(source, trigger)) {
          if (trigger.getPlatformAdId().equals(source.getDebugAdId())
              && isEnrollmentIdWithinUniqueAdIdLimit(source.getEnrollmentId())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
          }
          break;
        }
        if (canMatchJoinKeys(source, trigger)) {
          joinKeyHash = 0L;
          if (source.getDebugJoinKey().equals(trigger.getDebugJoinKey())) {
            sourceDebugKey = source.getDebugKey();
            triggerDebugKey = trigger.getDebugKey();
            joinKeyHash = (long) source.getDebugJoinKey().hashCode();
            doDebugJoinKeysMatch = true;
          }
        }
        break;
      default:
        break;
    }
    HashMap<UnsignedLong, UnsignedLong> res = new HashMap<>();
    res.put(sourceDebugKey, triggerDebugKey);
    return res;
  }

  private static boolean canMatchJoinKeys(Source source, Trigger trigger) {
    return Objects.nonNull(source.getDebugJoinKey()) && Objects.nonNull(trigger.getDebugJoinKey());
  }

  private static boolean canMatchAdIdAppSourceToWebTrigger(Source source, Trigger trigger) {
    return trigger.hasArDebugPermission()
        && Objects.nonNull(source.getPlatformAdId())
        && Objects.nonNull(trigger.getDebugAdId());
  }

  private static boolean canMatchAdIdWebSourceToAppTrigger(Source source, Trigger trigger) {
    return source.hasArDebugPermission()
        && Objects.nonNull(source.getDebugAdId())
        && Objects.nonNull(trigger.getPlatformAdId());
  }

  private boolean isEnrollmentIdWithinUniqueAdIdLimit(String enrollmentId) {
    long numUnique = mMeasurementDao.countDistinctDebugAdIdsUsedByEnrollment(enrollmentId);
    return numUnique < mFlags.getMeasurementPlatformDebugAdIdMatchingLimit();
  }

  private AttributionType getAttributionType(Source source, Trigger trigger) {
    boolean isSourceApp = source.getPublisherType() == EventSurfaceType.APP;
    if (trigger.getDestinationType() == EventSurfaceType.WEB) {
      return isSourceApp
          ? AttributionType.SOURCE_APP_TRIGGER_WEB
          : AttributionType.SOURCE_WEB_TRIGGER_WEB;
    } else {
      return isSourceApp
          ? AttributionType.SOURCE_APP_TRIGGER_APP
          : AttributionType.SOURCE_WEB_TRIGGER_APP;
    }
  }
}

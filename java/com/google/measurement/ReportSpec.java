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

import com.google.measurement.noising.Combinatorics;
import com.google.measurement.util.UnsignedLong;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.logging.Logger;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class ReportSpec implements Serializable {
  private static final Logger logger = Logger.getLogger(AttributionJobHandler.class.getName());
  private TriggerSpec[] mTriggerSpecs = null;
  private int mMaxEventLevelReports = 0;
  private PrivacyComputationParams mPrivacyParams = null;
  // Reference to a list that is a property of the Source object.
  private List<AttributedTrigger> mAttributedTriggersRef;

  /**
   * This constructor is called during the source registration process.
   *
   * @param triggerSpecsString input trigger specs from ad tech
   * @param maxEventLevelReports max event level reports from ad tech
   * @param source the source associated with this report spec
   */
  public ReportSpec(String triggerSpecsString, int maxEventLevelReports, Source source) {
    if (triggerSpecsString.isEmpty()) {
      return;
    }
    JSONParser parser = new JSONParser();
    JSONArray triggerSpecs;
    try {
      triggerSpecs = (JSONArray) parser.parse(triggerSpecsString);
      mTriggerSpecs = new TriggerSpec[triggerSpecs.size()];
      for (int i = 0; i < triggerSpecs.size(); i++) {
        mTriggerSpecs[i] = new TriggerSpec.Builder((JSONObject) triggerSpecs.get(i)).build();
      }
      mMaxEventLevelReports = maxEventLevelReports;
      mPrivacyParams = new PrivacyComputationParams();
      if (source != null) {
        mAttributedTriggersRef = source.getAttributedTriggers();
      }
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  /**
   * This constructor is called during the attribution process. Current trigger status will be read
   * and process to determine the outcome of incoming trigger.
   *
   * @param triggerSpecsString input trigger specs from ad tech
   * @param maxEventLevelReports max event level reports from ad tech
   * @param source the source associated with this report spec
   * @param privacyParametersString computed privacy parameters
   */
  public ReportSpec(
      String triggerSpecsString,
      int maxEventLevelReports,
      Source source,
      String privacyParametersString) {
    if (triggerSpecsString == null || triggerSpecsString.isEmpty()) {
      return;
    }
    JSONParser parser = new JSONParser();
    try {
      JSONArray triggerSpecs = (JSONArray) parser.parse(triggerSpecsString);
      mTriggerSpecs = new TriggerSpec[triggerSpecs.size()];
      for (int i = 0; i < triggerSpecs.size(); i++) {
        mTriggerSpecs[i] = new TriggerSpec.Builder((JSONObject) triggerSpecs.get(i)).build();
      }

      mMaxEventLevelReports = maxEventLevelReports;

      if (source != null) {
        mAttributedTriggersRef = source.getAttributedTriggers();
      }

      mPrivacyParams = new PrivacyComputationParams(privacyParametersString);
    } catch (ParseException e) {
      e.printStackTrace();
    }
  }

  /**
   * @return the probability to use fake report
   */
  public double getFlipProbability() {
    return getPrivacyParams().getFlipProbability();
  }

  /**
   * @return the information gain
   */
  public double getInformationGain() {
    return getPrivacyParams().mInformationGain;
  }

  /**
   * @return the number of states
   */
  public int getNumberState() {
    return getPrivacyParams().getNumStates();
  }

  /**
   * Get the parameters for the privacy computation. 1st element: total report cap, an array with 1
   * element is used to store the integer; 2nd element: number of windows per trigger data type; 3rd
   * element: number of report cap per trigger data type.
   *
   * @return the parameters to computer number of states and fake report
   */
  public int[][] getPrivacyParamsForComputation() {
    int[][] params = new int[3][];
    params[0] = new int[] {mMaxEventLevelReports};
    params[1] = mPrivacyParams.getPerTypeNumWindowList();
    params[2] = mPrivacyParams.getPerTypeCapList();
    return params;
  }

  /**
   * Encode the result of privacy parameters computed based on input parameters to JSON
   *
   * @return String encoded the privacy parameters
   */
  public String encodePrivacyParametersToJSONString() {
    JSONObject json = new JSONObject();
    json.put(
        ReportSpecUtil.FlexEventReportJsonKeys.FLIP_PROBABILITY, mPrivacyParams.mFlipProbability);
    return json.toJSONString();
  }

  private PrivacyComputationParams getPrivacyParams() {
    return mPrivacyParams;
  }

  /**
   * getter method for mTriggerSpecs
   *
   * @return the array of TriggerSpec
   */
  public TriggerSpec[] getTriggerSpecs() {
    return mTriggerSpecs;
  }

  /**
   * @return Max bucket increments. (a.k.a max number of reports)
   */
  public int getMaxReports() {
    return mMaxEventLevelReports;
  }

  /**
   * Get the trigger data type given a trigger data index. In the flexible event API, the trigger
   * data is not necessary input as [0, 1, 2..]
   *
   * @param triggerDataIndex The index of the triggerData
   * @return the value of the trigger data
   */
  public UnsignedLong getTriggerDataValue(int triggerDataIndex) {
    for (TriggerSpec triggerSpec : mTriggerSpecs) {
      int prevTriggerDataIndex = triggerDataIndex;
      triggerDataIndex -= triggerSpec.getTriggerData().size();
      if (triggerDataIndex < 0) {
        return triggerSpec.getTriggerData().get(prevTriggerDataIndex);
      }
    }
    // will not reach here
    return null;
  }

  /**
   * Get the reporting window end time given a trigger data and window index
   *
   * @param triggerDataIndex The index of the triggerData
   * @param windowIndex the window index, not the actual window end time
   * @return the report window end time
   */
  public long getWindowEndTime(int triggerDataIndex, int windowIndex) {
    for (TriggerSpec triggerSpec : mTriggerSpecs) {
      triggerDataIndex -= triggerSpec.getTriggerData().size();
      if (triggerDataIndex < 0) {
        return triggerSpec.getEventReportWindowsEnd().get(windowIndex);
      }
    }
    // will not reach here
    return -1;
  }

  /**
   * Encode the privacy reporting parameters to JSON
   *
   * @return json object encode this class
   */
  public String encodeTriggerSpecsToJson() {
    return encodeTriggerSpecsToJson(mTriggerSpecs);
  }

  private int[] computerPerTypeNumWindowList() {
    List<Integer> list = new ArrayList<>();
    for (TriggerSpec triggerSpec : mTriggerSpecs) {
      for (UnsignedLong ignored : triggerSpec.getTriggerData()) {
        list.add(triggerSpec.getEventReportWindowsEnd().size());
      }
    }
    return list.stream().mapToInt(Integer::intValue).toArray();
  }

  /**
   * Define the report level priority if multiple trigger contribute to a report. Incoming priority
   * will be compared with previous triggers priority to get the highest priority
   *
   * @param triggerData the trigger data
   * @param incomingPriority the priority of incoming trigger of this trigger data
   * @return the highest priority of this trigger data
   */
  public long getHighestPriorityOfAttributedAndIncomingTriggers(
      UnsignedLong triggerData, Long incomingPriority) {
    long highestPriority = Long.MIN_VALUE;
    if (mAttributedTriggersRef != null) {
      for (AttributedTrigger trigger : mAttributedTriggersRef) {
        if (Objects.equals(trigger.getTriggerData(), triggerData)) {
          highestPriority = Long.max(highestPriority, trigger.getPriority());
        }
      }
    }
    highestPriority = Long.max(highestPriority, incomingPriority);
    return highestPriority;
  }

  public List<AttributedTrigger> getAttributedTriggers() {
    return mAttributedTriggersRef;
  }

  private int[] computerPerTypeCapList() {
    List<Integer> list = new ArrayList<>();
    for (TriggerSpec triggerSpec : mTriggerSpecs) {
      for (UnsignedLong ignored : triggerSpec.getTriggerData()) {
        list.add(triggerSpec.getSummaryBucket().size());
      }
    }
    return list.stream().mapToInt(Integer::intValue).toArray();
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof ReportSpec)) {
      return false;
    }
    ReportSpec t = (ReportSpec) obj;
    if (mTriggerSpecs.length != t.mTriggerSpecs.length) {
      return false;
    }
    for (int i = 0; i < mTriggerSpecs.length; i++) {
      if (!mTriggerSpecs[i].equals(t.mTriggerSpecs[i])) {
        return false;
      }
    }
    if (mAttributedTriggersRef != null) {
      for (int i = 0; i < mAttributedTriggersRef.size(); i++) {
        if (!mAttributedTriggersRef.get(i).equals(t.mAttributedTriggersRef.get(i))) {
          return false;
        }
      }
    }
    return mMaxEventLevelReports == t.mMaxEventLevelReports;
  }

  @Override
  public int hashCode() {
    return Objects.hash(Arrays.hashCode(mTriggerSpecs), mMaxEventLevelReports);
  }

  /**
   * Encodes provided {@link TriggerSpec} into {@link JSONArray} string.
   *
   * @param triggerSpecs triggerSpec array to be encoded
   * @return JSON encoded String
   */
  public static String encodeTriggerSpecsToJson(TriggerSpec[] triggerSpecs) {
    JSONArray result = new JSONArray();
    for (TriggerSpec triggerSpec : triggerSpecs) {
      result.add(triggerSpec.encodeJSON());
    }
    return result.toJSONString();
  }

  private class PrivacyComputationParams implements Serializable {
    private final int[] mPerTypeNumWindowList;
    private final int[] mPerTypeCapList;
    private final int mNumStates;
    private double mFlipProbability = 0.0;
    private final double mInformationGain;

    PrivacyComputationParams() {
      mPerTypeNumWindowList = computerPerTypeNumWindowList();
      mPerTypeCapList = computerPerTypeCapList();
      // Check the upper bound of the parameters
      if (Math.min(mMaxEventLevelReports, Arrays.stream(mPerTypeCapList).sum())
          > PrivacyParams.MAX_FLEXIBLE_EVENT_REPORTS) {
        throw new IllegalArgumentException(
            "Max Event Reports Exceeds " + PrivacyParams.MAX_FLEXIBLE_EVENT_REPORTS);
      }
      if (mPerTypeNumWindowList.length
          > PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY) {
        throw new IllegalArgumentException(
            "Trigger Data Cardinality Exceeds "
                + PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY);
      }
      // check duplication of the trigger data
      Set<UnsignedLong> seen = new HashSet<>();
      for (TriggerSpec triggerSpec : mTriggerSpecs) {
        for (UnsignedLong num : triggerSpec.getTriggerData()) {
          if (!seen.add(num)) {
            throw new IllegalArgumentException("Duplication in Trigger Data");
          }
        }
      }
      // compute number of state and other privacy parameters
      mNumStates =
          Combinatorics.getNumStatesFlexAPI(
              mMaxEventLevelReports, mPerTypeNumWindowList, mPerTypeCapList);
      mFlipProbability = Combinatorics.getFlipProbability(mNumStates);
      mInformationGain = Combinatorics.getInformationGain(mNumStates, mFlipProbability);
      if (mInformationGain > PrivacyParams.MAX_FLEXIBLE_EVENT_INFORMATION_GAIN) {
        throw new IllegalArgumentException(
            "Information Gain Exceeds " + PrivacyParams.MAX_FLEXIBLE_EVENT_INFORMATION_GAIN);
      }
    }

    PrivacyComputationParams(String inputLine) {

      mPerTypeNumWindowList = null;
      mPerTypeCapList = null;
      mNumStates = -1;
      mInformationGain = -1.0;
      JSONParser parser = new JSONParser();
      try {
        JSONObject json = (JSONObject) parser.parse(inputLine);
        mFlipProbability =
            (Double) json.get(ReportSpecUtil.FlexEventReportJsonKeys.FLIP_PROBABILITY);
      } catch (ParseException e) {
        e.printStackTrace();
      }
    }

    private double getFlipProbability() {
      return mFlipProbability;
    }

    private int getNumStates() {
      return mNumStates;
    }

    private int[] getPerTypeNumWindowList() {
      return mPerTypeNumWindowList;
    }

    private int[] getPerTypeCapList() {
      return mPerTypeCapList;
    }
  }

  long findCurrentAttributedValue(UnsignedLong triggerData) {
    long result = 0L;
    if (mAttributedTriggersRef == null) {
      return result;
    }
    for (AttributedTrigger trigger : mAttributedTriggersRef) {
      if (Objects.equals(trigger.getTriggerData(), triggerData)) {
        result += trigger.getValue();
      }
    }
    return result;
  }

  /**
   * @param triggerData the triggerData to be checked
   * @return whether the triggerData is registered
   */
  public boolean containsTriggerData(UnsignedLong triggerData) {
    for (TriggerSpec triggerSpec : mTriggerSpecs) {
      for (UnsignedLong registeredTriggerData : triggerSpec.getTriggerData()) {
        if (Objects.equals(registeredTriggerData, triggerData)) {
          return true;
        }
      }
    }
    return false;
  }

  /**
   * Obtaining trigger value from trigger id.
   *
   * @param triggerId the trigger id for query
   * @return the value from the queried trigger id
   */
  public long getTriggerValue(String triggerId) {
    for (AttributedTrigger trigger : mAttributedTriggersRef) {
      if (trigger.getTriggerId().equals(triggerId)) {
        return trigger.getValue();
      }
    }
    return 0L;
  }

  /**
   * Record the trigger in the attribution status
   *
   * @param eventReport incoming report
   */
  public void insertAttributedTrigger(EventReport eventReport) {
    if (mAttributedTriggersRef == null) {
      mAttributedTriggersRef = new ArrayList<>();
    }
    mAttributedTriggersRef.add(
        new AttributedTrigger(
            eventReport.getTriggerId(),
            eventReport.getTriggerPriority(),
            eventReport.getTriggerData(),
            eventReport.getTriggerValue(),
            eventReport.getTriggerTime(),
            eventReport.getTriggerDedupKey()));
  }

  /**
   * Delete the history of an event report
   *
   * @param eventReport the event report to be deleted
   */
  public boolean deleteFromAttributedValue(EventReport eventReport) {
    Iterator<AttributedTrigger> iterator = mAttributedTriggersRef.iterator();
    while (iterator.hasNext()) {
      AttributedTrigger element = iterator.next();
      if (element.getTriggerId().equals(eventReport.getTriggerId())) {
        iterator.remove();
        return true;
      }
    }
    logger.info("ReportSpec::deleteFromAttributedValue: eventReport cannot be found");
    return false;
  }
}

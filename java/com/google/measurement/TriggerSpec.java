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
import com.google.measurement.util.Util;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

/**
 * A class wrapper for the trigger specification from the input argument during source registration
 */
public class TriggerSpec implements Serializable {
  private List<UnsignedLong> mTriggerData;
  private long mEventReportWindowsStart;
  private List<Long> mEventReportWindowsEnd;
  private SummaryOperatorType mSummaryWindowOperator;
  private List<Long> mSummaryBucket;

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof TriggerSpec)) {
      return false;
    }
    TriggerSpec t = (TriggerSpec) obj;
    return mTriggerData.equals(t.mTriggerData)
        && mEventReportWindowsStart == t.mEventReportWindowsStart
        && mEventReportWindowsEnd.equals(t.mEventReportWindowsEnd)
        && mSummaryWindowOperator == t.mSummaryWindowOperator
        && mSummaryBucket.equals(t.mSummaryBucket);
  }

  @Override
  public int hashCode() {
    return Objects.hash(
        mTriggerData,
        mEventReportWindowsStart,
        mEventReportWindowsEnd,
        mSummaryWindowOperator,
        mSummaryBucket);
  }

  /**
   * @return Trigger Data
   */
  public List<UnsignedLong> getTriggerData() {
    return mTriggerData;
  }

  /**
   * @return Event Report Windows Start
   */
  public long getEventReportWindowsStart() {
    return mEventReportWindowsStart;
  }

  /**
   * @return Event Report Windows End
   */
  public List<Long> getEventReportWindowsEnd() {
    return mEventReportWindowsEnd;
  }

  /**
   * @return Summary Window Operator
   */
  public SummaryOperatorType getSummaryWindowOperator() {
    return mSummaryWindowOperator;
  }

  /**
   * @return Summary Bucket
   */
  public List<Long> getSummaryBucket() {
    return mSummaryBucket;
  }

  /**
   * Encode the parameter to JSON
   *
   * @return json object encode this class
   */
  public JSONObject encodeJSON() {
    JSONObject json = new JSONObject();
    JSONArray triggerData = new JSONArray();
    triggerData.addAll(mTriggerData);
    json.put("trigger_data", triggerData);

    JSONObject windows = new JSONObject();
    windows.put("start_time", mEventReportWindowsStart);
    JSONArray eventReportWindowsEnd = new JSONArray();
    eventReportWindowsEnd.addAll(mEventReportWindowsEnd);
    windows.put("end_times", eventReportWindowsEnd);
    json.put("event_report_windows", windows);
    json.put("summary_window_operator", mSummaryWindowOperator.name().toLowerCase());

    JSONArray summaryBuckets = new JSONArray();
    summaryBuckets.addAll(mSummaryBucket);
    json.put("summary_buckets", summaryBuckets);
    return json;
  }

  public static <T extends Comparable<T>> boolean isStrictIncreasing(List<T> list) {
    for (int i = 1; i < list.size(); i++) {
      if (list.get(i).compareTo(list.get(i - 1)) <= 0) {
        return false;
      }
    }
    return true;
  }

  /** The choice of the summary operator with the reporting window */
  public enum SummaryOperatorType {
    COUNT,
    VALUE_SUM
  }

  public static ArrayList<Long> getLongArrayFromJSON(JSONObject json, String key) {
    ArrayList<Long> result = new ArrayList<>();
    JSONArray valueArray = (JSONArray) json.get(key);
    for (Object o : valueArray) {
      Long value = (long) o;
      result.add(value);
    }
    return result;
  }

  private void validateParameters() {
    if (!isStrictIncreasing(mEventReportWindowsEnd)) {
      throw new IllegalArgumentException(FieldsKey.EVENT_REPORT_WINDOWS + " not increasing");
    }
    if (!isStrictIncreasing(mSummaryBucket)) {
      throw new IllegalArgumentException(FieldsKey.SUMMARY_BUCKETS + " not increasing");
    }
    if (mEventReportWindowsStart < 0) {
      mEventReportWindowsStart = 0;
    }
  }

  /** */
  public static final class Builder {
    private final TriggerSpec mBuilding;

    public Builder(JSONObject jsonObject) throws IllegalArgumentException {
      mBuilding = new TriggerSpec();
      mBuilding.mSummaryWindowOperator = SummaryOperatorType.COUNT;
      mBuilding.mEventReportWindowsStart = 0;
      mBuilding.mSummaryBucket = new ArrayList<>();
      mBuilding.mEventReportWindowsEnd = new ArrayList<>();
      List<UnsignedLong> triggerData = new ArrayList<>();
      for (long num : getLongArrayFromJSON(jsonObject, FieldsKey.TRIGGER_DATA)) {
        triggerData.add(new UnsignedLong(num));
      }
      this.setTriggerData(triggerData);
      if (mBuilding.mTriggerData.size()
          > PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY) {
        throw new IllegalArgumentException(
            "Trigger Data Cardinality Exceeds "
                + PrivacyParams.MAX_FLEXIBLE_EVENT_TRIGGER_DATA_CARDINALITY);
      }
      JSONObject jsonReportWindows = (JSONObject) jsonObject.get(FieldsKey.EVENT_REPORT_WINDOWS);
      if (jsonReportWindows.containsKey(FieldsKey.START_TIME)) {
        this.setEventReportWindowsStart(Util.parseJsonInt(jsonReportWindows, FieldsKey.START_TIME));
      }
      JSONArray jsonArray = (JSONArray) jsonReportWindows.get(FieldsKey.END_TIME);
      if (jsonArray.size() > PrivacyParams.MAX_FLEXIBLE_EVENT_REPORTING_WINDOWS) {
        throw new IllegalArgumentException(
            "Number of Reporting Windows Exceeds "
                + PrivacyParams.MAX_FLEXIBLE_EVENT_REPORTING_WINDOWS);
      }
      List<Long> data = new ArrayList<>();
      for (Object o : jsonArray) {
        data.add((Long) o);
      }
      this.setEventReportWindowsEnd(data);
      if (jsonObject.containsKey(FieldsKey.SUMMARY_WINDOW_OPERATOR)) {
        try {
          SummaryOperatorType op =
              SummaryOperatorType.valueOf(
                  ((String) jsonObject.get(FieldsKey.SUMMARY_WINDOW_OPERATOR)).toUpperCase());
          this.setSummaryWindowOperator(op);
        } catch (IllegalArgumentException e) {
          // if a summary window operator is defined, but not in the pre-defined list, it
          // will throw to exception.
          throw new IllegalArgumentException(FieldsKey.SUMMARY_WINDOW_OPERATOR + " invalid");
        }
      }
      this.setSummaryBucket(getLongArrayFromJSON(jsonObject, FieldsKey.SUMMARY_BUCKETS));
      mBuilding.validateParameters();
    }

    /** See {@link TriggerSpec#getTriggerData()}. */
    public Builder setTriggerData(List<UnsignedLong> triggerData) {
      mBuilding.mTriggerData = triggerData;
      return this;
    }

    public Builder(JSONObject jsonObject, long defaultStart, List<Long> defaultWindowEnds)
        throws IllegalArgumentException {
      mBuilding = new TriggerSpec();
      mBuilding.mSummaryWindowOperator = SummaryOperatorType.COUNT;
      mBuilding.mEventReportWindowsStart = defaultStart;
      mBuilding.mSummaryBucket = new ArrayList<>();
      mBuilding.mEventReportWindowsEnd = defaultWindowEnds;
      JSONParser jsonParser = new JSONParser();
      try {

        this.setTriggerData(
            getTriggerDataArrayFromJSON(
                jsonObject, ReportSpecUtil.FlexEventReportJsonKeys.TRIGGER_DATA));
        if (jsonObject.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS)) {
          JSONObject jsonReportWindows =
              (JSONObject)
                  jsonParser.parse(
                      ((JSONObject)
                              jsonObject.get(
                                  ReportSpecUtil.FlexEventReportJsonKeys.EVENT_REPORT_WINDOWS))
                          .toJSONString());
          if (jsonReportWindows.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME)) {
            long startTime =
                (Long) jsonReportWindows.get(ReportSpecUtil.FlexEventReportJsonKeys.START_TIME);
            this.setEventReportWindowsStart(TimeUnit.SECONDS.toMillis(startTime));
          }

          this.setEventReportWindowsEnd(
              getLongArrayFromJSON(
                      jsonReportWindows, ReportSpecUtil.FlexEventReportJsonKeys.END_TIMES)
                  .stream()
                  .map(TimeUnit.SECONDS::toMillis)
                  .collect(Collectors.toList()));
        }

        if (jsonObject.containsKey(
            ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR)) {
          this.setSummaryWindowOperator(
              SummaryOperatorType.valueOf(
                  ((String)
                          jsonObject.get(
                              ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_WINDOW_OPERATOR))
                      .toUpperCase()));
        }
        if (jsonObject.containsKey(ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS)) {
          this.setSummaryBucket(
              getLongArrayFromJSON(
                  jsonObject, ReportSpecUtil.FlexEventReportJsonKeys.SUMMARY_BUCKETS));
        }
      } catch (ParseException e) {
        Logger.getLogger(getClass().getName()).info("Cannot parse JSON");
      }
    }

    /** See {@link TriggerSpec#getEventReportWindowsStart()}. */
    public Builder setEventReportWindowsStart(long eventReportWindowsStart) {
      mBuilding.mEventReportWindowsStart = eventReportWindowsStart;
      return this;
    }

    /** See {@link TriggerSpec#getEventReportWindowsEnd()}. */
    public Builder setEventReportWindowsEnd(List<Long> eventReportWindowsEnd) {
      mBuilding.mEventReportWindowsEnd = eventReportWindowsEnd;
      return this;
    }

    /** See {@link TriggerSpec#getSummaryWindowOperator()}. */
    public Builder setSummaryWindowOperator(SummaryOperatorType summaryWindowOperator) {
      mBuilding.mSummaryWindowOperator = summaryWindowOperator;
      return this;
    }

    /** See {@link TriggerSpec#getSummaryBucket()}. */
    public Builder setSummaryBucket(List<Long> summaryBucket) {
      mBuilding.mSummaryBucket = summaryBucket;
      return this;
    }

    /** Build the {@link TriggerSpec}. */
    public TriggerSpec build() {
      return mBuilding;
    }
  }

  private interface FieldsKey {
    String END_TIME = "end_times";
    String START_TIME = "start_time";
    String SUMMARY_WINDOW_OPERATOR = "summary_window_operator";
    String EVENT_REPORT_WINDOWS = "event_report_windows";
    String TRIGGER_DATA = "trigger_data";
    String SUMMARY_BUCKETS = "summary_buckets";
  }

  /**
   * Parses long JSONArray into List<UnsignedLong>
   *
   * @param json parent jsonObject
   * @param key key for the JSON Array
   * @return a list of UnsignedLong
   */
  public static List<UnsignedLong> getTriggerDataArrayFromJSON(JSONObject json, String key) {
    List<UnsignedLong> result = new ArrayList<>();
    JSONArray valueArray = (JSONArray) json.get(key);
    for (Object o : valueArray) {
      result.add(new UnsignedLong((Long) o));
    }
    return result;
  }
}

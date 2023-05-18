Different batching strategies can be implemented in
`java/com/google/measurement/adtech/BatchAgggregatableReports.java`

For example, the current daily batching strategy can be modified to perform weekly instead:

The relevant line of code is
```java
long formattedDate = Util.roundDownToDay((long) sharedInfo.get("scheduled_report_time"));
```

You can create an additional method in the Util class for rounding down to week and invoke that
instead:

```java
long formattedDate = Util.roundDownToWeek((long) sharedInfo.get("scheduled_report_time"));
```

Doing so would produce larger batches of Aggregatable Reports.

Note, although this Simulation does not encrypt the Aggregatable Reports' payloads, the actual
Aggregate API will, so only filter based on properties found in the `shared_info` object. See
`java/com/google/measurement/adtech/AggregateReport.java` for how the `shared_info` object is populated.
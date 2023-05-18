# Domain directory

## Purpose
The purpose of this subdirectory is to house the domain.avro files necessary for
 Aggregation API.

The domain.avro file specifies which buckets in a batch of Aggregatable Reports
should be aggregated.

By default, the Measurement Simulation Library ignores this directory 
because the skipDomain property in AggregationArgs.properties is set to 
true, however, if that property is set to false, the library will search 
this directory for domain.avro files.

## Usage
Each batch of Aggregatable Reports needs its own domain.avro file. In order for
Measurement Simulation Library to find the appropriate domain.avro file, the
subdirectories in this directory will be named after the batch key of the batch.
The batch key of the batch is determined by the batching strategy specified in
adtech/BatchAggregatableReports.java. By default, a daily batching strategy is
used. Measurement Simulation Library takes an additional step in sanitizing the
batch key so that it conforms with Unix file name rules, namely, special
characters are transformed into the hyphen character.


### Example
The default daily batching strategy in the Measurement Simulation Library
batches all Aggregatable Reports with the same destination and that are
scheduled to be sent on the same day. For example, a set of input data could
have attributions for the https://example.com destination occurring on 1/1/2023.
Following the logic in adtech/BatchAggregatableReports.java, the batch key for
this batch would be https://example.com_1672531200000 (Note, the date 1/1/2023
is converted to the Unix epoch timestamp format). Then, the batch key is
sanitized to produce https---example-com_1672531200000. This value is what
Measurement Simulation Library searches for when fetching the domain.avro file
for this batch. So under this directory there should be a subdirectory with
that label and under that subdirectory is a file named domain.avro, so:
domain/https---example-com_1672531200000/domain.avro
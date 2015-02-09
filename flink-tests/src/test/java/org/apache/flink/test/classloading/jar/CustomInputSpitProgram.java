/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.test.classloading.jar;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.io.InputFormat;
import org.apache.flink.api.common.io.statistics.BaseStatistics;
import org.apache.flink.api.common.typeinfo.BasicTypeInfo;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.DataSet;
import org.apache.flink.api.java.ExecutionEnvironment;
import org.apache.flink.api.java.io.DiscardingOutputFormat;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.typeutils.ResultTypeQueryable;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.core.io.InputSplit;
import org.apache.flink.core.io.InputSplitAssigner;
import org.apache.flink.core.memory.DataInputView;
import org.apache.flink.core.memory.DataOutputView;

@SuppressWarnings("serial")
public class CustomInputSpitProgram {
	
	public static void main(String[] args) throws Exception {
		
		final String jarFile = args[0];
		final String host = args[1];
		final int port = Integer.parseInt(args[2]);
		
		ExecutionEnvironment env = ExecutionEnvironment.createRemoteEnvironment(host, port, jarFile);

		DataSet<Integer> data = env.createInput(new CustomInputFormat());

		data
			.map(new MapFunction<Integer, Tuple2<Integer, Double>>() {
				@Override
				public Tuple2<Integer, Double> map(Integer value) {
					return new Tuple2<Integer, Double>(value, value * 0.5);
				}
			})
			.output(new DiscardingOutputFormat<Tuple2<Integer,Double>>());

		env.execute();
	}
	// --------------------------------------------------------------------------------------------
	
	public static final class CustomInputFormat implements InputFormat<Integer, CustomInputSplit>, ResultTypeQueryable<Integer> {

		private static final long serialVersionUID = 1L;

		private Integer value;

		@Override
		public void configure(Configuration parameters) {}

		@Override
		public BaseStatistics getStatistics(BaseStatistics cachedStatistics) {
			return null;
		}

		@Override
		public CustomInputSplit[] createInputSplits(int minNumSplits) {
			CustomInputSplit[] splits = new CustomInputSplit[minNumSplits];
			for (int i = 0; i < minNumSplits; i++) {
				splits[i] = new CustomInputSplit(i);
			}
			return splits;
		}

		@Override
		public InputSplitAssigner getInputSplitAssigner(CustomInputSplit[] inputSplits) {
			return new CustomSplitAssigner(inputSplits);
		}

		@Override
		public void open(CustomInputSplit split) {
			this.value = split.getSplitNumber();
		}

		@Override
		public boolean reachedEnd() {
			return this.value == null;
		}

		@Override
		public Integer nextRecord(Integer reuse) {
			Integer val = this.value;
			this.value = null;
			return val;
		}

		@Override
		public void close() {}

		@Override
		public TypeInformation<Integer> getProducedType() {
			return BasicTypeInfo.INT_TYPE_INFO;
		}
	}

	public static final class CustomInputSplit implements InputSplit {

		private static final long serialVersionUID = 1L;

		private int splitNumber;

		public CustomInputSplit() {
			this(-1);
		}

		public CustomInputSplit(int splitNumber) {
			this.splitNumber = splitNumber;
		}

		@Override
		public int getSplitNumber() {
			return this.splitNumber;
		}

		@Override
		public void write(DataOutputView out) throws IOException {
			out.writeInt(splitNumber);
		}

		@Override
		public void read(DataInputView in) throws IOException {
			splitNumber = in.readInt();
		}
	}

	public static final class CustomSplitAssigner implements InputSplitAssigner {

		private final List<CustomInputSplit> remainingSplits;

		public CustomSplitAssigner(CustomInputSplit[] splits) {
			this.remainingSplits = new ArrayList<CustomInputSplit>(Arrays.asList(splits));
		}

		@Override
		public InputSplit getNextInputSplit(String host) {
			synchronized (this) {
				int size = remainingSplits.size();
				if (size > 0) {
					return remainingSplits.remove(size - 1);
				} else {
					return null;
				}
			}
		}
	}
}

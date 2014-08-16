/*
 * Copyright (C) 2014 The Calrissian Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.calrissian.flowmix.bolt;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import backtype.storm.task.OutputCollector;
import backtype.storm.task.TopologyContext;
import backtype.storm.topology.OutputFieldsDeclarer;
import backtype.storm.topology.base.BaseRichBolt;
import backtype.storm.tuple.Tuple;
import backtype.storm.tuple.Values;
import org.calrissian.flowmix.FlowmixFactory;
import org.calrissian.flowmix.model.Flow;
import org.calrissian.flowmix.model.FlowInfo;
import org.calrissian.flowmix.model.op.SplitOp;
import org.calrissian.flowmix.support.Filter;
import org.calrissian.flowmix.support.Utils;
import org.calrissian.mango.domain.Pair;

import static org.calrissian.flowmix.FlowmixFactory.fields;
import static org.calrissian.flowmix.spout.MockFlowLoaderSpout.FLOW_LOADER_STREAM;

public class SplitBolt extends BaseRichBolt {

  Map<String,Flow> flows;
  OutputCollector collector;

  @Override
  public void prepare(Map map, TopologyContext topologyContext, OutputCollector outputCollector) {
    this.collector = outputCollector;
    flows = new HashMap<String,Flow>();
  }

  @Override
  public void execute(Tuple tuple) {

    if (FLOW_LOADER_STREAM.equals(tuple.getSourceStreamId())) {
      for (Flow flow : (Collection<Flow>) tuple.getValue(0))
        flows.put(flow.getId(), flow);
    } else if (!"tick".equals(tuple.getSourceStreamId())) {

      FlowInfo flowInfo = new FlowInfo(tuple);

      Flow flow = flows.get(flowInfo.getFlowId());

      if (flow != null) {
        SplitOp splitOp = (SplitOp) flow.getStream(flowInfo.getStreamName()).getFlowOps().get(flowInfo.getIdx());

        String nextStream = Utils.getNextStreamFromFlowInfo(flowInfo, flow);

        // first check the default path
        Filter filter = splitOp.getDefaultPath();

        if(filter != null && filter.accept(flowInfo.getEvent())) {
          if ((nextStream.equals("output") && flow.getStream(flowInfo.getStreamName()).isStdOutput()) || !nextStream.equals("output"))
              collector.emit(nextStream, tuple, new Values(flowInfo.getFlowId(), flowInfo.getEvent(), flowInfo.getIdx(), flowInfo.getStreamName(), flowInfo.getPreviousStream()));

          // send directly to any non std output streams
          if (nextStream.equals("output") && flow.getStream(flowInfo.getStreamName()).getOutputs() != null) {
            for (String output : flow.getStream(flowInfo.getStreamName()).getOutputs()) {
              String outputStream = flow.getStream(output).getFlowOps().get(0).getComponentName();
              collector.emit(outputStream, tuple, new Values(flowInfo.getFlowId(), flowInfo.getEvent(), -1, output, flowInfo.getStreamName()));
            }
          }
        }

        // then check all other paths
        if(splitOp.getPaths() != null) {
          for(Pair<Filter, String> pathPair : splitOp.getPaths()) {
            if(pathPair.getOne().accept(flowInfo.getEvent())) {
              String outputStream = flow.getStream(pathPair.getTwo()).getFlowOps().get(0).getComponentName();
              collector.emit(outputStream, tuple, new Values(flowInfo.getFlowId(), flowInfo.getEvent(), -1, pathPair.getTwo(), flowInfo.getStreamName()));
            }
          }
        }
      }
    }
    collector.ack(tuple);
  }

  @Override
  public void declareOutputFields(OutputFieldsDeclarer outputFieldsDeclarer) {
    FlowmixFactory.declareOutputStreams(outputFieldsDeclarer, fields);
  }
}

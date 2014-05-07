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
package org.calrissian.flowbox.model;

import org.calrissian.flowbox.model.builder.FlowBuilder;
import org.calrissian.flowbox.support.Criteria;
import org.calrissian.flowbox.support.aggregator.LongSumAggregator;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

public class FlowBuilderTest {

    @Test
    public void testInitialFlowBuilder() {
        Flow flow = new FlowBuilder()
            .id("myTestFlow")
            .name("My First Test Flow")
            .description("This is a test flow just to prove that we can use the builder effectively")
            .flowDefs()
                .stream("stream1")
                    .filter().criteria(new Criteria() {
                        @Override
                        public boolean matches(Event event) {
                            return false;
                        }
                    }).end()
                    .select().fields("name", "age").end()
                    .partition().fields("name", "age", "country").end()
                    .aggregate().aggregator(LongSumAggregator.class).evict(Policy.COUNT, 500).trigger(Policy.TIME, 25).end()
                    .stopGate().activate(Policy.TIME_DELTA_LT, 1).evict(Policy.COUNT, 5).open(Policy.TIME, 60).end()
                .endStream()
            .endDefs()
        .createFlow();

        assertEquals("myTestFlow", flow.getId());
        assertEquals("My First Test Flow", flow.getName());
        assertEquals("This is a test flow just to prove that we can use the builder effectively", flow.getDescription());
        assertEquals(5, flow.getStreams().iterator().next().getFlowOps().size());
        assertEquals(2, ((SelectOp) flow.getStreams().iterator().next().getFlowOps().get(1)).getFields().size());
    }

    @Test
    public void testNoStdOutputthrowsException() {

      try {
        Flow flow = new FlowBuilder()
          .id("myTestFlow")
          .flowDefs()
          .stream("stream1")
          .select().fields("name", "age").end()
          .endStream(false, null)
          .endDefs()
          .createFlow();

        fail("An exception should have been thrown");
      } catch(Exception e) {}
    }

    @Test
    public void testNoStdInputthrowsException() {

      try {
        Flow flow = new FlowBuilder()
          .id("myTestFlow")
          .flowDefs()
          .stream("stream1", false)
          .select().fields("name", "age").end()
          .endStream()
          .endDefs()
          .createFlow();

        fail("An exception should have been thrown");
      } catch(Exception e) {}
    }

    @Test
    public void testInvalidJoin_NoLHSThrowsException() {

      try {
        Flow flow = new FlowBuilder()
                .id("myTestFlow")
                .flowDefs()
                .stream("stream1", false)
                .select().fields("name", "age").end()
                .endStream()
                .stream("stream2")
                .select().fields("hello").end()
                .endStream(new String[] {"stream3"})
                .stream("stream3")
                .join("stream1", "stream2").end()
                .endStream()
                .endDefs()
                .createFlow();

        fail("An exception should have been thrown");
      } catch(Exception e) {}
    }

    @Test
    public void testInvalidJoin_NoRHSThrowsException() {

      try {
        Flow flow = new FlowBuilder()
                .id("myTestFlow")
                .flowDefs()
                .stream("stream1", false)
                .select().fields("name", "age").end()
                .endStream(new String[] {"stream3"})
                .stream("stream2")
                .select().fields("hello").end()
                .endStream()
                .stream("stream3")
                .join("stream1", "stream2").end()
                .endStream()
                .endDefs()
                .createFlow();

        fail("An exception should have been thrown");
      } catch(Exception e) {}
    }
}

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.rocketmq.snode.client.impl;

import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.RemotingChannel;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.snode.SnodeController;
import org.apache.rocketmq.snode.client.SlowConsumerService;

public class SlowConsumerServiceImpl implements SlowConsumerService {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.SNODE_LOGGER_NAME);

    private final SnodeController snodeController;

    public SlowConsumerServiceImpl(SnodeController snodeController) {
        this.snodeController = snodeController;
    }

    @Override
    public boolean isSlowConsumer(long currentOffset, String topic, int queueId,
        String consumerGroup, String enodeName) {
        long ackedOffset = this.snodeController.getConsumerOffsetManager().queryCacheOffset(enodeName, consumerGroup, topic, queueId);
        if (currentOffset - ackedOffset > snodeController.getSnodeConfig().getSlowConsumerThreshold()) {
            log.warn("[SlowConsumer] group: {}, lastAckedOffset:{} nowOffset:{} ", consumerGroup, ackedOffset, currentOffset);
            return true;
        }
        return false;
    }

    @Override
    public void slowConsumerResolve(RemotingCommand pushMessage, RemotingChannel remotingChannel) {
        log.warn("[SlowConsumer] RemotingChannel address: {}", remotingChannel.remoteAddress());
    }
}

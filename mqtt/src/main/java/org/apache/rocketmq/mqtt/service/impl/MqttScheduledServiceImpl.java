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
package org.apache.rocketmq.mqtt.service.impl;

import io.netty.handler.codec.mqtt.MqttMessageType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.common.service.ScheduledService;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.mqtt.client.IOTClientManagerImpl;
import org.apache.rocketmq.mqtt.client.InFlightMessage;
import org.apache.rocketmq.mqtt.client.InFlightPacket;
import org.apache.rocketmq.mqtt.client.MQTTSession;
import org.apache.rocketmq.mqtt.constant.MqttConstant;
import org.apache.rocketmq.mqtt.processor.DefaultMqttMessageProcessor;
import org.apache.rocketmq.remoting.transport.mqtt.MqttHeader;

public class MqttScheduledServiceImpl implements ScheduledService {
    private static final InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.MQTT_LOGGER_NAME);

    private final DefaultMqttMessageProcessor defaultMqttMessageProcessor;

    public MqttScheduledServiceImpl(DefaultMqttMessageProcessor defaultMqttMessageProcessor) {
        this.defaultMqttMessageProcessor = defaultMqttMessageProcessor;
    }

    private final ScheduledExecutorService mqttScheduledExecutorService = Executors.newSingleThreadScheduledExecutor(new ThreadFactory() {
        @Override
        public Thread newThread(Runnable r) {
            return new Thread(r, "MqttScheduledThread");
        }
    });

    @Override
    public void startScheduleTask() {

        this.mqttScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                IOTClientManagerImpl iotClientManager = (IOTClientManagerImpl) defaultMqttMessageProcessor.getIotClientManager();
                ConcurrentHashMap<String, ConcurrentHashMap<String, TreeMap<Long, MessageExt>>> processTable = iotClientManager.getProcessTable();
                for (Map.Entry<String, ConcurrentHashMap<String, TreeMap<Long, MessageExt>>> entry : processTable.entrySet()) {
                    String brokerName = entry.getKey();
                    ConcurrentHashMap<String, TreeMap<Long, MessageExt>> map = entry.getValue();
                    for (Map.Entry<String, TreeMap<Long, MessageExt>> innerEntry : map.entrySet()) {
                        String topicClient = innerEntry.getKey();
                        TreeMap<Long, MessageExt> inflightMessages = innerEntry.getValue();
                        Long offset = inflightMessages.firstKey();
                        defaultMqttMessageProcessor.getEnodeService().persistOffset(null, brokerName, topicClient.split("@")[1], topicClient.split("@")[0], 0, offset);
                    }
                }
            }
        }, 0, defaultMqttMessageProcessor.getMqttConfig().getPersistOffsetInterval(), TimeUnit.MILLISECONDS);
        this.mqttScheduledExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override public void run() {
                IOTClientManagerImpl iotClientManager = (IOTClientManagerImpl) defaultMqttMessageProcessor.getIotClientManager();
                Collection<InFlightPacket> expired = new ArrayList<>();
                iotClientManager.getInflightTimeouts().drainTo(expired);
                for (InFlightPacket notAcked : expired) {
                    MQTTSession client = notAcked.getClient();
                    if (!client.isConnected()) {
                        continue;
                    }
                    if (notAcked.getResendTime() > 3) {
                        client.getRemotingChannel().close();
                        continue;
                    }
                    if (client.getInflightWindow().containsKey(notAcked.getPacketId())) {
                        InFlightMessage inFlightMessage = client.getInflightWindow().get(notAcked.getPacketId());
                        MqttHeader mqttHeader = new MqttHeader();
                        mqttHeader.setTopicName(inFlightMessage.getTopic());
                        mqttHeader.setQosLevel(inFlightMessage.getPushQos());
                        mqttHeader.setRetain(false);
                        mqttHeader.setDup(true);
                        mqttHeader.setMessageType(MqttMessageType.PUBLISH.value());
                        notAcked.setStartTime(System.currentTimeMillis() + MqttConstant.FLIGHT_BEFORE_RESEND_MS);
                        notAcked.setResendTime(notAcked.getResendTime() + 1);
                        iotClientManager.getInflightTimeouts().add(notAcked);
                        client.pushMessage2Client(mqttHeader, inFlightMessage.getBody());
                    }
                }
            }
        }, 10000, defaultMqttMessageProcessor.getMqttConfig().getScanAckTimeoutInterval(), TimeUnit.MILLISECONDS);
    }

    @Override
    public void shutdown() {
        if (this.mqttScheduledExecutorService != null) {
            this.mqttScheduledExecutorService.shutdown();
        }
    }
}

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

package org.apache.rocketmq.remoting.transport.mqtt;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;
import io.netty.handler.codec.mqtt.MqttMessage;
import io.netty.handler.codec.mqtt.MqttMessageType;
import java.util.List;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.transport.mqtt.dispatcher.EncodeDecodeDispatcher;
import org.apache.rocketmq.remoting.transport.mqtt.dispatcher.Message2MessageEncodeDecode;

public class RemotingCommand2MqttMessageHandler extends MessageToMessageEncoder<RemotingCommand> {

    /**
     * Encode from one message to an other. This method will be called for each written message that can be handled by
     * this encoder.
     *
     * @param ctx the {@link ChannelHandlerContext} which this {@link MessageToMessageEncoder} belongs to
     * @param msg the message to encode to an other one
     * @param out the {@link List} into which the encoded msg should be added needs to do some kind of aggregation
     * @throws Exception is thrown if an error occurs
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, RemotingCommand msg, List<Object> out)
        throws Exception {
        if (!(msg instanceof RemotingCommand)) {
            return;
        }
        MqttMessage mqttMessage;
        MqttHeader mqttHeader = (MqttHeader) msg.readCustomHeader();
        Message2MessageEncodeDecode message2MessageEncodeDecode = EncodeDecodeDispatcher
            .getEncodeDecodeDispatcher().get(
                MqttMessageType.valueOf(mqttHeader.getMessageType()));
        if (message2MessageEncodeDecode == null) {
            throw new IllegalArgumentException(
                "Unknown message type: " + mqttHeader.getMessageType());
        }
        mqttMessage = message2MessageEncodeDecode.encode(msg);
        out.add(mqttMessage);
    }
}

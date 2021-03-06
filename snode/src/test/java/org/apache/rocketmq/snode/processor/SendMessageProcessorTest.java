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
package org.apache.rocketmq.snode.processor;

import java.util.concurrent.CompletableFuture;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.MqttConfig;
import org.apache.rocketmq.common.SnodeConfig;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.SendMessageRequestHeaderV2;
import org.apache.rocketmq.common.service.EnodeService;
import org.apache.rocketmq.common.service.NnodeService;
import org.apache.rocketmq.remoting.ClientConfig;
import org.apache.rocketmq.remoting.RemotingChannel;
import org.apache.rocketmq.remoting.ServerConfig;
import org.apache.rocketmq.remoting.exception.RemotingCommandException;
import org.apache.rocketmq.remoting.netty.CodecHelper;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.snode.SnodeController;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.MockitoJUnitRunner;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class SendMessageProcessorTest {

    private SendMessageProcessor sendMessageProcessor;

    @Spy
    private ServerConfig serverConfig = new ServerConfig();
    @Spy
    private ClientConfig clientConfig = new ClientConfig();
    @Spy
    private ServerConfig mqttServerConfig = new ServerConfig();
    @Spy
    private ClientConfig mqttClientConfig = new ClientConfig();

    private SnodeController snodeController;

    @Mock
    private RemotingChannel remotingChannel;

    private String topic = "snodeTopic";

    private String group = "snodeGroup";

    @Mock
    private EnodeService enodeService;

    @Mock
    private NnodeService nnodeService;

    public SendMessageProcessorTest() {
    }

    @Before
    public void init() throws CloneNotSupportedException {
        SnodeConfig snodeConfig = new SnodeConfig();
        serverConfig.setListenPort(snodeConfig.getListenPort());
        snodeConfig.setNettyClientConfig(clientConfig);
        snodeConfig.setNettyServerConfig(serverConfig);

        MqttConfig mqttConfig = new MqttConfig();
        mqttServerConfig.setListenPort(mqttConfig.getListenPort());
        mqttConfig.setMqttClientConfig(mqttClientConfig);
        mqttConfig.setMqttServerConfig(mqttServerConfig);

        snodeController = new SnodeController(snodeConfig, mqttConfig);
        snodeController.setNnodeService(nnodeService);
        snodeController.setEnodeService(enodeService);
        sendMessageProcessor = new SendMessageProcessor(snodeController);
    }

    @Test
    public void testSendMessageV2ProcessRequest() throws RemotingCommandException {
        CompletableFuture<RemotingCommand> future = new CompletableFuture<>();
        RemotingCommand request = createSendMesssageV2Command();
        when(this.snodeController.getEnodeService().sendMessage(any(RemotingChannel.class), anyString(), any(RemotingCommand.class))).thenReturn(future);
        sendMessageProcessor.processRequest(remotingChannel, request);
    }

    @Test
    public void testSendBatchMessageProcessRequest() throws RemotingCommandException {
        snodeController.setEnodeService(enodeService);
        CompletableFuture<RemotingCommand> future = new CompletableFuture<>();
        RemotingCommand request = createSendBatchMesssageCommand();
        when(this.snodeController.getEnodeService().sendMessage(any(RemotingChannel.class), anyString(), any(RemotingCommand.class))).thenReturn(future);
        sendMessageProcessor.processRequest(remotingChannel, request);
    }

    private SendMessageRequestHeaderV2 createSendMsgRequestHeader() {
        SendMessageRequestHeaderV2 requestHeader = new SendMessageRequestHeaderV2();
        requestHeader.setA(group);
        requestHeader.setB(topic);
        requestHeader.setC(MixAll.AUTO_CREATE_TOPIC_KEY_TOPIC);
        requestHeader.setD(3);
        requestHeader.setE(1);
        requestHeader.setF(0);
        requestHeader.setG(System.currentTimeMillis());
        requestHeader.setH(124);
        requestHeader.setN("enodeName");
        return requestHeader;
    }

    private RemotingCommand createSendMesssageV2Command() {
        SendMessageRequestHeaderV2 sendMessageRequestHeaderV2 = createSendMsgRequestHeader();
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_MESSAGE_V2, sendMessageRequestHeaderV2);
        request.setBody(new byte[] {'a'});
        CodecHelper.makeCustomHeaderToNet(request);
        return request;
    }

    private RemotingCommand createSendBatchMesssageCommand() {
        SendMessageRequestHeaderV2 sendMessageRequestHeaderV2 = createSendMsgRequestHeader();
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.SEND_BATCH_MESSAGE, sendMessageRequestHeaderV2);
        request.setBody(new byte[] {'b'});
        CodecHelper.makeCustomHeaderToNet(request);
        return request;
    }

    RemotingCommand createSendMessageResponse(int responseCode) {
        return RemotingCommand.createResponseCommand(ResponseCode.SUCCESS, null);
    }

    CompletableFuture<RemotingCommand> createResponseCompletableFuture(int responseCode) {
        CompletableFuture<RemotingCommand> completableFuture = new CompletableFuture<>();
        return completableFuture;
    }
}

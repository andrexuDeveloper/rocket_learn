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
package org.apache.rocketmq.snode.service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.apache.rocketmq.common.MqttConfig;
import org.apache.rocketmq.common.SnodeConfig;
import org.apache.rocketmq.common.protocol.RequestCode;
import org.apache.rocketmq.common.protocol.ResponseCode;
import org.apache.rocketmq.common.protocol.header.PullMessageRequestHeader;
import org.apache.rocketmq.common.service.EnodeService;
import org.apache.rocketmq.common.service.NnodeService;
import org.apache.rocketmq.remoting.ClientConfig;
import org.apache.rocketmq.remoting.InvokeCallback;
import org.apache.rocketmq.remoting.ServerConfig;
import org.apache.rocketmq.remoting.netty.ResponseFuture;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.remoting.transport.rocketmq.NettyRemotingClient;
import org.apache.rocketmq.snode.SnodeController;
import org.apache.rocketmq.snode.SnodeTestBase;
import org.apache.rocketmq.snode.service.impl.RemoteEnodeServiceImpl;
import org.apache.rocketmq.store.GetMessageResult;
import org.apache.rocketmq.store.GetMessageStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class RemoteEnodeServiceImplTest extends SnodeTestBase {

    private EnodeService enodeService;

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
    private NnodeService nnodeService;

    @Mock
    private NettyRemotingClient remotingClient;

    private String enodeName = "enodeName";

    private String topic = "snodeTopic";

    private String group = "snodeGroup";

    public RemoteEnodeServiceImplTest() {
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
        snodeController.setRemotingClient(remotingClient);
        enodeService = new RemoteEnodeServiceImpl(snodeController);
    }

    @Test
    public void sendMessageTest() throws Exception {
        when(snodeController.getNnodeService().getAddressByEnodeName(anyString(), anyBoolean())).thenReturn("127.0.0.1:10911");
        CompletableFuture<RemotingCommand> responseCF = new CompletableFuture<>();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                InvokeCallback callback = mock.getArgument(3);
                RemotingCommand request = mock.getArgument(1);
                ResponseFuture responseFuture = new ResponseFuture(null, request.getOpaque(), 3 * 1000, null, null);
                responseFuture.setResponseCommand(createSuccessResponse());
                callback.operationComplete(responseFuture);
                responseCF.complete(createSuccessResponse());
                return null;
            }
        }).when(remotingClient).invokeAsync(anyString(), any(RemotingCommand.class), anyLong(), any(InvokeCallback.class));
        RemotingCommand response = enodeService.sendMessage(null, enodeName, createSendMesssageCommand(group, topic)).get(3000L, TimeUnit.MILLISECONDS);
        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS);
    }

    @Test
    public void pullMessageTest() throws Exception {
        snodeController.setNnodeService(nnodeService);
        snodeController.setRemotingClient(remotingClient);
        when(snodeController.getNnodeService().getAddressByEnodeName(anyString(), anyBoolean())).thenReturn("1024");
        CompletableFuture<RemotingCommand> responseCF = new CompletableFuture<>();
        doAnswer(new Answer() {
            @Override
            public Object answer(InvocationOnMock mock) throws Throwable {
                InvokeCallback callback = mock.getArgument(3);
                RemotingCommand request = mock.getArgument(1);
                ResponseFuture responseFuture = new ResponseFuture(null, request.getOpaque(), 3 * 1000, null, null);
                RemotingCommand remotingCommand = createSuccessResponse();
                GetMessageResult getMessageResult = createGetMessageResult();
                remotingCommand.encodeHeader(getMessageResult.getBufferTotalSize());
                responseFuture.setResponseCommand(remotingCommand);
                responseCF.complete(remotingCommand);
                callback.operationComplete(responseFuture);
                return null;
            }
        }).when(remotingClient).invokeAsync(anyString(), any(RemotingCommand.class), anyLong(), any(InvokeCallback.class));
        RemotingCommand response = enodeService.pullMessage(null, enodeName, createPullMessage()).get(3000L, TimeUnit.MILLISECONDS);
        assertThat(response).isNotNull();
        assertThat(response.getCode()).isEqualTo(ResponseCode.SUCCESS);
    }

    private GetMessageResult createGetMessageResult() {
        GetMessageResult getMessageResult = new GetMessageResult();
        getMessageResult.setStatus(GetMessageStatus.FOUND);
        getMessageResult.setMinOffset(100);
        getMessageResult.setMaxOffset(1024);
        getMessageResult.setNextBeginOffset(516);
        return getMessageResult;
    }

    private RemotingCommand createPullMessage() {
        PullMessageRequestHeader requestHeader = new PullMessageRequestHeader();
        requestHeader.setCommitOffset(123L);
        requestHeader.setConsumerGroup(group);
        requestHeader.setMaxMsgNums(100);
        requestHeader.setQueueId(1);
        requestHeader.setQueueOffset(456L);
        requestHeader.setSubscription("*");
        requestHeader.setTopic(topic);
        requestHeader.setSysFlag(0);
        requestHeader.setSubVersion(100L);
        RemotingCommand request = RemotingCommand.createRequestCommand(RequestCode.PULL_MESSAGE, requestHeader);
        return request;
    }

}

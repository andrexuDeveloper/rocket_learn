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
package org.apache.rocketmq.snode;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.PosixParser;
import org.apache.rocketmq.broker.BrokerController;
import org.apache.rocketmq.broker.BrokerStartup;
import org.apache.rocketmq.common.MixAll;
import org.apache.rocketmq.common.MqttConfig;
import org.apache.rocketmq.common.SnodeConfig;
import org.apache.rocketmq.common.constant.LoggerName;
import org.apache.rocketmq.logging.InternalLogger;
import org.apache.rocketmq.logging.InternalLoggerFactory;
import org.apache.rocketmq.remoting.ClientConfig;
import org.apache.rocketmq.remoting.ServerConfig;
import org.apache.rocketmq.remoting.common.RemotingUtil;
import org.apache.rocketmq.remoting.common.TlsMode;
import org.apache.rocketmq.remoting.netty.TlsSystemConfig;
import org.apache.rocketmq.remoting.protocol.RemotingCommand;
import org.apache.rocketmq.srvutil.ServerUtil;
import org.slf4j.LoggerFactory;

import static org.apache.rocketmq.remoting.netty.TlsSystemConfig.TLS_ENABLE;

public class SnodeStartup {
    private static InternalLogger log = InternalLoggerFactory.getLogger(LoggerName.SNODE_LOGGER_NAME);
    public static Properties properties = null;
    public static CommandLine commandLine = null;
    public static String configFile = null;
    private static final String DEFAULT_MQTT_CONFIG_FILE = "/conf/mqtt.properties";
    private static String mqttConfigFileName = System.getProperty("rocketmq.mqtt.config", DEFAULT_MQTT_CONFIG_FILE);

    public static void main(String[] args) throws IOException, JoranException, CloneNotSupportedException {
        SnodeConfig snodeConfig = loadConfig(args);
        MqttConfig mqttConfig = loadMqttConfig(snodeConfig);
        if (snodeConfig.isEmbeddedModeEnable()) {
            BrokerController brokerController = BrokerStartup.createBrokerController(args);
            BrokerStartup.start(brokerController);
            snodeConfig.setSnodeName(brokerController.getBrokerConfig().getBrokerName());
        }
        SnodeController snodeController = createSnodeController(snodeConfig, mqttConfig);
        startup(snodeController);
    }

    public static SnodeController startup(SnodeController controller) {
        try {
            controller.start();
            String tip = "The snode[" + controller.getSnodeConfig().getSnodeName() + ", "
                + controller.getSnodeConfig().getSnodeIP1() + "] boot success. serializeType=" + RemotingCommand.getSerializeTypeConfigInThisServer();

            if (null != controller.getSnodeConfig().getNamesrvAddr()) {
                tip += " and name server is " + controller.getSnodeConfig().getNamesrvAddr();
            }
            log.info(tip);
            System.out.printf("%s%n", tip);
            return controller;
        } catch (Throwable e) {
            e.printStackTrace();
            System.exit(-1);
        }
        return null;
    }

    public static SnodeConfig loadConfig(String[] args) throws IOException {
        Options options = ServerUtil.buildCommandlineOptions(new Options());
        commandLine = ServerUtil.parseCmdLine("snode", args, buildCommandlineOptions(options),
            new PosixParser());
        if (null == commandLine) {
            System.exit(-1);
        }

        SnodeConfig snodeConfig = new SnodeConfig();
        final ServerConfig nettyServerConfig = new ServerConfig();
        final ClientConfig nettyClientConfig = new ClientConfig();
        nettyServerConfig.setListenPort(snodeConfig.getListenPort());
        nettyClientConfig.setUseTLS(Boolean.parseBoolean(System.getProperty(TLS_ENABLE,
            String.valueOf(TlsSystemConfig.tlsMode == TlsMode.ENFORCING))));

        if (commandLine.hasOption('c')) {
            String file = commandLine.getOptionValue('c');
            if (file != null) {
                configFile = file;
                InputStream in = new BufferedInputStream(new FileInputStream(file));
                properties = new Properties();
                properties.load(in);
                MixAll.properties2Object(properties, snodeConfig);
                MixAll.properties2Object(properties, nettyServerConfig);
                MixAll.properties2Object(properties, nettyClientConfig);

                in.close();
            }
        }
        snodeConfig.setNettyServerConfig(nettyServerConfig);
        snodeConfig.setNettyClientConfig(nettyClientConfig);
        if (null == snodeConfig.getRocketmqHome()) {
            System.out.printf("Please set the %s variable in your environment to match the location of the RocketMQ installation", MixAll.ROCKETMQ_HOME_ENV);
            System.exit(-2);
        }

        MixAll.properties2Object(ServerUtil.commandLine2Properties(commandLine), snodeConfig);
        String namesrvAddr = snodeConfig.getNamesrvAddr();
        if (null != namesrvAddr) {
            try {
                String[] addrArray = namesrvAddr.split(";");
                for (String addr : addrArray) {
                    RemotingUtil.string2SocketAddress(addr);
                }
            } catch (Exception e) {
                System.out.printf(
                    "The Name Server Address[%s] illegal, please set it as follows, \"127.0.0.1:9876;192.168.0.1:9876\"%n",
                    namesrvAddr);
                System.exit(-3);
            }
        }

        MixAll.printObjectProperties(log, snodeConfig);
        MixAll.printObjectProperties(log, snodeConfig.getNettyServerConfig());
        MixAll.printObjectProperties(log, snodeConfig.getNettyClientConfig());
        return snodeConfig;
    }

    public static MqttConfig loadMqttConfig(SnodeConfig snodeConfig) throws IOException {
        MqttConfig mqttConfig = new MqttConfig();
        final ServerConfig mqttServerConfig = new ServerConfig();
        final ClientConfig mqttClientConfig = new ClientConfig();
        mqttServerConfig.setListenPort(mqttConfig.getListenPort());
        String file = snodeConfig.getRocketmqHome() + File.separator + mqttConfigFileName;
        loadMqttProperties(file, mqttServerConfig, mqttClientConfig);
        mqttConfig.setMqttServerConfig(mqttServerConfig);
        mqttConfig.setMqttClientConfig(mqttClientConfig);

        MixAll.printObjectProperties(log, mqttConfig);
        MixAll.printObjectProperties(log, mqttConfig.getMqttServerConfig());
        MixAll.printObjectProperties(log, mqttConfig.getMqttClientConfig());
        return mqttConfig;
    }

    public static SnodeController createSnodeController(SnodeConfig snodeConfig, MqttConfig mqttConfig) throws JoranException, CloneNotSupportedException {

        final SnodeController snodeController = new SnodeController(snodeConfig, mqttConfig);

        boolean initResult = snodeController.initialize();
        if (!initResult) {
            snodeController.shutdown();
            System.exit(-3);
        }

        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            private volatile boolean hasShutdown = false;
            private AtomicInteger shutdownTimes = new AtomicInteger(0);

            @Override
            public void run() {
                synchronized (this) {
                    log.info("Shutdown hook was invoked, {}", this.shutdownTimes.incrementAndGet());

                    if (!this.hasShutdown) {
                        this.hasShutdown = true;
                        long beginTime = System.currentTimeMillis();
                        snodeController.shutdown();
                        long consumingTimeTotal = System.currentTimeMillis() - beginTime;
                        log.info("Shutdown hook over, consuming total time(ms): {}", consumingTimeTotal);
                    }
                }
            }
        }, "ShutdownHook"));
        LoggerContext lc = (LoggerContext) LoggerFactory.getILoggerFactory();
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(lc);
        lc.reset();
        configurator.doConfigure(snodeConfig.getRocketmqHome() + "/conf/logback_snode.xml");
        log = InternalLoggerFactory.getLogger(LoggerName.SNODE_LOGGER_NAME);

        return snodeController;
    }

    private static Options buildCommandlineOptions(final Options options) {
        Option opt = new Option("c", "configFile", true, "SNode config properties file");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("p", "printConfigItem", false, "Print all config item");
        opt.setRequired(false);
        options.addOption(opt);

        opt = new Option("m", "printImportantConfig", false, "Print important config item");
        opt.setRequired(false);
        options.addOption(opt);

        return options;
    }

    private static void loadMqttProperties(String file, ServerConfig mqttServerConfig,
        ClientConfig mqttClientConfig) throws IOException {
        InputStream in;
        try {
            in = new BufferedInputStream(new FileInputStream(file));
            Properties properties = new Properties();
            properties.load(in);
            MixAll.properties2Object(properties, mqttServerConfig);
            MixAll.properties2Object(properties, mqttClientConfig);
            in.close();
        } catch (FileNotFoundException e) {
            log.info("The mqtt config file is not found. filePath={}", file);
        }

    }
}


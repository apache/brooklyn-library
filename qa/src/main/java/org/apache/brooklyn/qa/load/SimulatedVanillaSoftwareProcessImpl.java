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
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.qa.load;

import java.net.URI;
import java.util.concurrent.Callable;

import org.apache.brooklyn.api.sensor.AttributeSensor;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.entity.BrooklynConfigKeys;
import org.apache.brooklyn.core.entity.lifecycle.ServiceStateLogic.ServiceNotUpLogic;
import org.apache.brooklyn.core.sensor.Sensors;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcessImpl;
import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcessSshDriver;
import org.apache.brooklyn.feed.function.FunctionFeed;
import org.apache.brooklyn.feed.function.FunctionPollConfig;
import org.apache.brooklyn.feed.http.HttpFeed;
import org.apache.brooklyn.feed.http.HttpPollConfig;
import org.apache.brooklyn.feed.http.HttpValueFunctions;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.time.Duration;

/**
 * For simulating various aspects of the {@link VanillaSoftwareProcess} entity.
 *  
 * It is assumed that the ssh commands for install, launch, etc will be written with testability in mind.
 * For example, they might just be {@code echo} statements, because there is insufficient resources to 
 * run 100s of processes.
 * 
 * It is thus possible to simulate aspects of the behaviour, for performance and load testing purposes. 
 * 
 * There is configuration for:
 * <ul>
 *   <li>{@code skipSshOnStart}
 *     <ul>
 *       <li>If true, then no ssh commands will be executed at deploy-time. 
 *           This is useful for speeding up load testing, to get to the desired number of entities.
 *       <li>If false, the ssh commands will be executed.
 *     </ul>
 *   <li>{@code simulateEntity}
 *     <ul>
 *       <li>if true, no underlying entity will be started. Instead a sleep 100000 job will be run and monitored.
 *       <li>if false, the underlying entity (i.e. a JBoss app-server) will be started as normal.
 *     </ul>
 *   <li>{@code simulateExternalMonitoring}
 *     <ul>
 *       <li>if true, disables the default monitoring mechanism. Instead, a function will periodically execute 
 *           to set the entity's sensors (as though the values had been obtained from the external monitoring tool).
 *       <li>if false, then:
 *         <ul>
 *           <li>If {@code simulateEntity==true} it will execute comparable commands (e.g. execute a command of the same 
 *               size over ssh or do a comparable number of http GET requests).
 *           <li>If {@code simulateEntity==false} then normal monitoring will be done.
 *         </ul>
 *     </ul>
 * </ul>
 */
public class SimulatedVanillaSoftwareProcessImpl extends VanillaSoftwareProcessImpl {

    public static final ConfigKey<Boolean> EXEC_SSH_ON_START = ConfigKeys.newBooleanConfigKey(
            "execSshOnStart", 
            "If true, will execute the ssh commands on install/launch; if false, will skip them", 
            true);

    public static final ConfigKey<URI> HTTP_FEED_URI = ConfigKeys.newConfigKey(
            URI.class, 
            "httpFeed.uri", 
            "If non-null, the URI to poll periodically using a HttpFeed", null);

    public static final ConfigKey<Duration> HTTP_FEED_POLL_PERIOD = ConfigKeys.newConfigKey(
            Duration.class, 
            "httpFeed.pollPeriod", 
            "The poll priod for the HttpFeed (if 'httpFeed.uri' was non-null)", 
            Duration.ONE_SECOND);

    public static final ConfigKey<Duration> FUNCTION_FEED_POLL_PERIOD = ConfigKeys.newConfigKey(
            Duration.class, 
            "functionFeed.pollPeriod", 
            "The poll priod for a function that increments 'counter' periodically (if null, then no-op)", 
            Duration.ONE_SECOND);

    // see SERVICE_PROCESS_IS_RUNNING_POLL_PERIOD
    // Inspired by EmptySoftwareProcess.USE_SSH_MONITORING
    public static final ConfigKey<Boolean> USE_SSH_MONITORING = ConfigKeys.newConfigKey(
            "sshMonitoring.enabled", 
            "Whether to poll periodically over ssh, using the driver.isRunning check", 
            Boolean.TRUE);

    private static final AttributeSensor<String> HTTP_STRING_ATTRIBUTE = Sensors.newStringSensor("httpStringAttribute");

    private static final AttributeSensor<Integer> HTTP_INT_ATTRIBUTE = Sensors.newIntegerSensor("httpIntAttribute");

    private static final AttributeSensor<Long> FUNCTION_COUNTER = Sensors.newLongSensor("functionCounter");

    private FunctionFeed functionFeed;
    private HttpFeed httpFeed;
    
    @Override
    public void init() {
        super.init();
        if (Boolean.FALSE.equals(config().get(EXEC_SSH_ON_START))) {
            config().set(BrooklynConfigKeys.SKIP_ON_BOX_BASE_DIR_RESOLUTION, true);
        }
    }
    
    @Override
    public Class<?> getDriverInterface() {
        return SimulatedVanillaSoftwareProcessSshDriver.class;
    }

    @Override
    protected void connectServiceUpIsRunning() {
        boolean useSshMonitoring = Boolean.TRUE.equals(config().get(USE_SSH_MONITORING));
        if (useSshMonitoring) {
            super.connectServiceUpIsRunning();
        }
    }
    
    @Override
    protected void initEnrichers() {
        super.initEnrichers();
    }
    
    @Override
    protected void connectSensors() {
        super.connectSensors();
        
        boolean useSshMonitoring = Boolean.TRUE.equals(config().get(USE_SSH_MONITORING));
        Duration functionFeedPeriod = config().get(FUNCTION_FEED_POLL_PERIOD);
        URI httpFeedUri = config().get(HTTP_FEED_URI);
        
        if (!useSshMonitoring) {
            ServiceNotUpLogic.clearNotUpIndicator(this, SERVICE_PROCESS_IS_RUNNING);
        }

        if (functionFeedPeriod != null) {
            functionFeed = feeds().add(FunctionFeed.builder()
                    .entity(this)
                    .period(functionFeedPeriod)
                    .poll(FunctionPollConfig.forSensor(FUNCTION_COUNTER)
                            .callable(new Callable<Long>() {
                                @Override public Long call() throws Exception {
                                    Long oldVal = sensors().get(FUNCTION_COUNTER);
                                    return (oldVal == null) ? 1 : oldVal + 1;
                                }
                            }))
                    .build());
        }
        
        if (httpFeedUri != null) {
            httpFeed = feeds().add(HttpFeed.builder()
                    .entity(this)
                    .period(config().get(HTTP_FEED_POLL_PERIOD))
                    .baseUri(httpFeedUri)
                    .poll(new HttpPollConfig<Integer>(HTTP_INT_ATTRIBUTE)
                            .onSuccess(HttpValueFunctions.responseCode()))
                    .poll(new HttpPollConfig<String>(HTTP_STRING_ATTRIBUTE)
                            .onSuccess(HttpValueFunctions.stringContentsFunction()))
                    .build());
        }
    }

    @Override
    protected void disconnectSensors() {
        super.disconnectSensors();
        if (functionFeed != null) functionFeed.stop();
        if (httpFeed != null) httpFeed.stop();
    }
    
    public static class SimulatedVanillaSoftwareProcessSshDriver extends VanillaSoftwareProcessSshDriver {
        public SimulatedVanillaSoftwareProcessSshDriver(SimulatedVanillaSoftwareProcessImpl entity, SshMachineLocation machine) {
            super(entity, machine);
        }
        
        @Override
        public void install() {
            if (Boolean.TRUE.equals(entity.getConfig(EXEC_SSH_ON_START))) {
                super.install();
            } else {
                // no-op
            }
        }
        
        @Override
        public void customize() {
            if (Boolean.TRUE.equals(entity.getConfig(EXEC_SSH_ON_START))) {
                super.customize();
            } else {
                // no-op
            }
        }
        
        @Override
        public void launch() {
            if (Boolean.TRUE.equals(entity.getConfig(EXEC_SSH_ON_START))) {
                super.launch();
            } else {
                // no-op
            }
        }
        
        @Override
        public boolean isRunning() {
            if (Boolean.TRUE.equals(entity.getConfig(USE_SSH_MONITORING))) {
                return super.isRunning();
            } else {
                return true;
            }
        }
    }
}

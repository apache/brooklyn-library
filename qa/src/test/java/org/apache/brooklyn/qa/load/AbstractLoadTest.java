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

import static org.testng.Assert.assertEquals;

import java.io.File;
import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.brooklyn.api.entity.Entity;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.api.mgmt.ManagementContext;
import org.apache.brooklyn.api.mgmt.ha.HighAvailabilityMode;
import org.apache.brooklyn.camp.brooklyn.AbstractYamlTest;
import org.apache.brooklyn.core.entity.StartableApplication;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.PortRanges;
import org.apache.brooklyn.core.mgmt.internal.LocalManagementContext;
import org.apache.brooklyn.core.mgmt.persist.PersistMode;
import org.apache.brooklyn.core.test.HttpService;
import org.apache.brooklyn.entity.group.DynamicCluster;
import org.apache.brooklyn.launcher.BrooklynLauncher;
import org.apache.brooklyn.policy.autoscaling.AutoScalerPolicy;
import org.apache.brooklyn.test.performance.PerformanceTestUtils;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.os.Os;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Stopwatch;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.io.Files;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;

/**
 * Customers ask about the scalability of Brooklyn. These load tests investigate how many 
 * concurrent apps can be deployed and managed by a single Brooklyn management node.
 * 
 * The apps are "simulated" in that they don't create the underlying resources 
 * (we are not checking if the test machine can run 100s of app-servers simultaneously!) 
 * 
 * See the configuration options on {@link SimulatedVanillaSoftwareProcessImpl}.
 * 
 * The {@link SimulatedExternalMonitor} is used to simulate us not polling the entities directly
 * (over ssh, http or whatever). Instead we simulate the metrics being retrieved from some external
 * source, and injected directly into the entities by calling {@code sensors().set()}. For example,
 * this could be collected from a Graphite server.
 * 
 * If using {@link TestConfig#simulateExternalMonitor(Predicate, int, Duration)}, it will 
 * automatically turn off {@code useSshMonitoring}, {@code useHttpMonitoring} and 
 * {@code useFunctionMonitoring} for <em>all</em> entities (not just for those that match 
 * the predicate passed to simulateExternalMonitor).
 */
public class AbstractLoadTest extends AbstractYamlTest {

    // TODO Could/should issue provisioning request through REST api, rather than programmatically; 
    // and poll to detect completion.
    
    /*
     * Useful commands when investigating:
     *     LOG_FILE=usage/qa/brooklyn-camp-tests.log
     *     grep -E "OutOfMemoryError|[P|p]rovisioning time|sleeping before|CPU fraction|LoadTest using" $LOG_FILE | less
     *     grep -E "OutOfMemoryError|[P|p]rovisioning time" $LOG_FILE; grep "CPU fraction" $LOG_FILE | tail -1; grep "LoadTest using" $LOG_FILE | tail -1
     *     grep -E "OutOfMemoryError|LoadTest using" $LOG_FILE
     */
    private static final Logger LOG = LoggerFactory.getLogger(AbstractLoadTest.class);

    private File persistenceDir;
    private BrooklynLauncher launcher;
    private ListeningExecutorService executor;
    private Future<?> cpuFuture;
    
    private Location localhost;
    
    List<Duration> provisioningTimes;

    private HttpService httpService;
    private URI httpServiceUri;

    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();
        
        localhost = mgmt().getLocationRegistry().getLocationManaged("localhost");
        
        provisioningTimes = Collections.synchronizedList(Lists.<Duration>newArrayList());

        // Create executors
        executor = MoreExecutors.listeningDecorator(Executors.newCachedThreadPool());

        // Monitor utilisation (memory/CPU) while tests run
        executor.submit(new Callable<Void>() {
            public Void call() {
                try {
                    mgmt().getExecutionManager(); // force GC to be instantiated
                    while (true) {
                        String usage = ((LocalManagementContext)mgmt()).getGarbageCollector().getUsageString();
                        LOG.info("LoadTest using "+usage);
                        Thread.sleep(1000);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt(); // exit gracefully
                } catch (Exception e) {
                    LOG.error("Error getting usage info", e);
                }
                return null;
            }});
        
        cpuFuture = PerformanceTestUtils.sampleProcessCpuTime(Duration.ONE_SECOND, "during LoadTest");
        
        httpService = new HttpService(PortRanges.fromString("9000+"), true).start();
        httpServiceUri = new URI(httpService.getUrl());
    }
    
    @AfterMethod(alwaysRun=true)
    public void tearDown() throws Exception {
        try {
            if (httpService != null) httpService.shutdown();
            if (cpuFuture != null) cpuFuture.cancel(true);
            if (executor != null) executor.shutdownNow();
        } finally {
            super.tearDown();
        }
    }

    @Override
    protected ManagementContext setUpPlatform() {
        // Create management node
        persistenceDir = Files.createTempDir();
        launcher = BrooklynLauncher.newInstance()
                .persistMode(doPersistence() ? PersistMode.CLEAN : PersistMode.DISABLED)
                .highAvailabilityMode(doPersistence() ? HighAvailabilityMode.MASTER : HighAvailabilityMode.DISABLED)
                .persistenceDir(persistenceDir)
                .start();
        
        String webServerUrl = launcher.getServerDetails().getWebServerUrl();
        LOG.info("Brooklyn web-console running at " + webServerUrl);
        
        return launcher.getServerDetails().getManagementContext();
    }

    protected boolean doPersistence() {
        return true;
    }
    
    @Override
    protected void tearDownPlatform() {
        if (launcher != null) launcher.terminate();
        if (persistenceDir != null) Os.deleteRecursively(persistenceDir);
    }
    
    public static class TestConfig {
        public int totalApps = 1;
        public int numAppsPerBatch = 1;
        public Duration sleepBetweenBatch = Duration.ZERO;
        
        int clusterSize = 2;
        
        boolean simulateExternalMonitor = false;
        Predicate<? super Entity> externalMonitorFilter;
        int externalMonitorNumSensors;
        Duration externalMonitorPollPeriod;
        
        
        boolean execSshOnStart = SimulatedVanillaSoftwareProcessImpl.EXEC_SSH_ON_START.getDefaultValue();
        Duration functionFeedPollPeriod = SimulatedVanillaSoftwareProcessImpl.FUNCTION_FEED_POLL_PERIOD.getDefaultValue();
        boolean useSshMonitoring = SimulatedVanillaSoftwareProcessImpl.USE_SSH_MONITORING.getDefaultValue();
        Duration httpFeedPollPeriod = SimulatedVanillaSoftwareProcessImpl.HTTP_FEED_POLL_PERIOD.getDefaultValue();
        URI httpFeedUri;
        
        public TestConfig(AbstractLoadTest tester) {
            httpFeedUri = tester.httpServiceUri;
        }
        public TestConfig simulateExternalMonitor(Predicate<? super Entity> filter, int numSensors, Duration pollPeriod) {
            simulateExternalMonitor = true;
            externalMonitorFilter = filter;
            externalMonitorNumSensors = numSensors;
            externalMonitorPollPeriod = pollPeriod;
            useSshMonitoring(false);
            useHttpMonitoring(false);
            useFunctionMonitoring(false);
            return this;
        }
        public TestConfig totalApps(int totalApps) {
            return totalApps(totalApps, totalApps);
        }
        public TestConfig totalApps(int totalApps, int numAppsPerBatch) {
            this.totalApps = totalApps;
            this.numAppsPerBatch = numAppsPerBatch;
            return this;
        }
        public TestConfig sleepBetweenBatch(Duration val) {
            sleepBetweenBatch = val;
            return this;
        }
        public TestConfig clusterSize(int val) {
            clusterSize = val;
            return this;
        }
        public TestConfig execSshOnStart(boolean val) {
            execSshOnStart = val;
            return this;
        }
        public TestConfig useSshMonitoring(boolean val) {
            useSshMonitoring = val;
            return this;
        }
        public TestConfig useHttpMonitoring(boolean val) {
            if (val) {
                if (httpFeedUri == null) {
                    throw new IllegalStateException("No HTTP URI; expected to be set by AbstractLoadTest.httpServiceUri");
                }
            } else {
                httpFeedUri = null;
            }
            return this;
        }
        public TestConfig useFunctionMonitoring(boolean val) {
            if (val) {
                functionFeedPollPeriod = SimulatedVanillaSoftwareProcessImpl.FUNCTION_FEED_POLL_PERIOD.getDefaultValue(); 
            } else {
                functionFeedPollPeriod = null;
            }
            return this;
        }
    }
    
    protected void runLocalhostManyApps(TestConfig config) throws Exception {
        final int totalApps = config.totalApps;
        final int numAppsPerBatch = config.numAppsPerBatch;
        final int numCycles = (totalApps / numAppsPerBatch);
        final Duration sleepBetweenBatch = config.sleepBetweenBatch;
        
        int counter = 0;
        
        if (config.simulateExternalMonitor) {
            SimulatedExternalMonitor externalMonitor = mgmt().getEntityManager().createEntity(EntitySpec.create(SimulatedExternalMonitor.class)
                    .configure(SimulatedExternalMonitor.ENTITY_FILTER, config.externalMonitorFilter)
                    .configure(SimulatedExternalMonitor.NUM_SENSORS, config.externalMonitorNumSensors)
                    .configure(SimulatedExternalMonitor.POLL_PERIOD, config.externalMonitorPollPeriod));
            externalMonitor.start(ImmutableList.<Location>of());
        }
        for (int i = 0; i < numCycles; i++) {
            List<ListenableFuture<? extends Entity>> futures = Lists.newArrayList();
            for (int j = 0; j < numAppsPerBatch; j++) {
                String yamlApp = newYamlApp("Simulated App " + i, config);
                
                ListenableFuture<? extends Entity> future = executor.submit(newProvisionAppTask(yamlApp));
                futures.add(future);
                counter++;
            }
            
            List<? extends Entity> apps = Futures.allAsList(futures).get();
            
            for (Entity app : apps) {
                assertEquals(app.getAttribute(Startable.SERVICE_UP), (Boolean)true);
            }

            synchronized (provisioningTimes) {
                LOG.info("cycle="+i+"; numApps="+counter+": provisioning times: "+provisioningTimes);
                provisioningTimes.clear();
            }

            LOG.info("cycle="+i+"; numApps="+counter+": sleeping for "+sleepBetweenBatch+" before next batch of apps");
            Time.sleep(sleepBetweenBatch);
        }
    }
    
    protected String newYamlApp(String appName, TestConfig config) {
        return Joiner.on("\n").join(
                "name: " + appName,
                "location: localhost",

                "services:",
                "- type: " + DynamicCluster.class.getName(),
                "  id: cluster",
                "  brooklyn.config:",
                "    cluster.initial.size: " + config.clusterSize,
                "    memberSpec:",
                "      $brooklyn:entitySpec:",
                "        type: " + SimulatedVanillaSoftwareProcessImpl.class.getName(),
                "        brooklyn.config:",
                "          shell.env:",
                "            ENV1: val1",
                "            ENV2: val2",
                "        install.command: echo myInstallCommand",
                "        customize.command: echo myCustomizeCommand",
                "        launch.command: echo myLaunchCommand",
                "        checkRunning.command: echo myCheckRunningCommand",
                "        " + SimulatedVanillaSoftwareProcessImpl.EXEC_SSH_ON_START.getName() + ": " + config.execSshOnStart,
                "        " + SimulatedVanillaSoftwareProcessImpl.USE_SSH_MONITORING.getName() + ": " + config.useSshMonitoring,
                "        " + SimulatedVanillaSoftwareProcessImpl.FUNCTION_FEED_POLL_PERIOD.getName() + ": " + config.functionFeedPollPeriod,
                "        " + SimulatedVanillaSoftwareProcessImpl.HTTP_FEED_POLL_PERIOD.getName() + ": " + config.httpFeedPollPeriod,
                "        " + (config.httpFeedUri != null ? SimulatedVanillaSoftwareProcessImpl.HTTP_FEED_URI.getName() + ": " + config.httpFeedUri : ""),
                "  brooklyn.enrichers:",
                "  - type: org.apache.brooklyn.enricher.stock.Aggregator",
                "    brooklyn.config:",
                "      enricher.sourceSensor: counter",
                "      enricher.targetSensor: counter",
                "      transformation: sum",
                "  brooklyn.policies:",
                "  - type: " + AutoScalerPolicy.class.getName(),
                "    brooklyn.config:",
                "      metric: sensorDoesNotExist",
                "      metricLowerBound: 1",
                "      metricUpperBound: 3",
                "      minPoolSize: " + config.clusterSize,
                "      maxPoolSize: " + (config.clusterSize + 3));
    }
    
    protected Callable<Entity> newProvisionAppTask(final String yaml) {
        return new Callable<Entity>() {
            public Entity call() throws Exception {
                try {
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    Entity app = createAndStartApplication(yaml);
                    Duration duration = Duration.of(stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
                    LOG.info("Provisioning time: "+duration);
                    provisioningTimes.add(duration);
    
                    return app;
                } catch (Throwable t) {
                    LOG.error("Error deploying app (rethrowing)", t);
                    throw Exceptions.propagate(t);
                }
            }
        };
    }
    
    protected <T extends StartableApplication> Callable<T> newProvisionAppTask(final EntitySpec<T> appSpec) {
        return new Callable<T>() {
            public T call() {
                try {
                    Stopwatch stopwatch = Stopwatch.createStarted();
                    T app = mgmt().getEntityManager().createEntity(appSpec);
                    app.start(ImmutableList.of(localhost));
                    Duration duration = Duration.of(stopwatch.elapsed(TimeUnit.MILLISECONDS), TimeUnit.MILLISECONDS);
                    LOG.info("Provisioning time: "+duration);
                    provisioningTimes.add(duration);
    
                    return app;
                } catch (Throwable t) {
                    LOG.error("Error deploying app (rethrowing)", t);
                    throw Exceptions.propagate(t);
                }
            }
        };
    }
}

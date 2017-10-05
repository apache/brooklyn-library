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

import org.apache.brooklyn.entity.software.base.VanillaSoftwareProcess;
import org.apache.brooklyn.util.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;

import com.google.common.base.Predicates;
import com.google.common.base.Stopwatch;

public class LoadWithoutPersistenceTest extends AbstractLoadTest {

    private static final Logger log = LoggerFactory.getLogger(LoadWithoutPersistenceTest.class);
    
    @Override
    protected boolean doPersistence() {
        return false;
    }

    /**
     * Creates many SSH simulated external monitor apps, to ensure resource usage not extreme.
     * 
     * Long-term target is 2500 VMs under management.
     * Until we reach that point, we can partition the load across multiple (separate) brooklyn management nodes.
     */
    @Test(groups="Acceptance")
    public void testManyAppsExternallyMonitored() throws Exception {
        // TODO Getting ssh error ("Server closed connection during identification exchange") 
        // with only two cycles (i.e. 20 entities).
        //
        // The ssh activity is from `SoftwareProcessImpl.waitForEntityStart`, which calls
        // `VanillaSoftwareProcessSshDriver.isRunning`.
        final int TOTAL_APPS = 500; // target is 2500 VMs; each blueprint has 2 VanillaSoftwareProcess
        final int NUM_APPS_PER_BATCH = 10;
        Stopwatch startTime = Stopwatch.createStarted();
        super.runLocalhostManyApps(new TestConfig(this)
                .execSshOnStart(false) // getting ssh errors otherwise!
                .useSshMonitoring(false) // getting ssh errors otherwise!
                .simulateExternalMonitor(Predicates.instanceOf(VanillaSoftwareProcess.class), 5, Duration.ONE_SECOND)
                .clusterSize(5)
                .totalApps(TOTAL_APPS, NUM_APPS_PER_BATCH)
                .sleepBetweenBatch(Duration.seconds(0)));
        log.info("Created "+TOTAL_APPS+" apps in "+Duration.of(startTime));
    }
    
}

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
import org.testng.annotations.Test;

import com.google.common.base.Predicates;

public class LoadTest extends AbstractLoadTest {

    /**
     * Creates multiple apps simultaneously. 
     * 
     * Long-term target is 50 concurrent provisioning requests (which may be issued while there are
     * many existing applications under management). Until we reach that point, we can partition the
     * load across multiple (separate) brooklyn management nodes.
     *   TODO TBD: is that 50 VMs worth, or 50 apps with 4 VMs in each? 
     * 
     * TODO Does not measure the cost of jclouds for creating all the VMs/containers.
     */
    @Test(groups="Acceptance")
    public void testProvisioningConcurrently() throws Exception {
        // TODO Getting ssh error (SocketException: Connection reset) with 10 entities, if don't disable ssh-on-start.
        // Will still execute checkRunning to wait for process to start (even if execSshOnStart is false).
        super.runLocalhostManyApps(new TestConfig(this)
                .useSshMonitoring(false)
                .execSshOnStart(false) // getting ssh errors otherwise!
                .totalApps(10));
    }

    /**
     * Creates many apps, to monitor resource usage etc.
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
        final int TOTAL_APPS = 600; // target is 2500 VMs; each blueprint has 2 VanillaSoftwareProcess
        final int NUM_APPS_PER_BATCH = 10;
        super.runLocalhostManyApps(new TestConfig(this)
                .execSshOnStart(false) // getting ssh errors otherwise!
                .simulateExternalMonitor(Predicates.instanceOf(VanillaSoftwareProcess.class), 5, Duration.ONE_SECOND)
                .clusterSize(2)
                .totalApps(TOTAL_APPS, NUM_APPS_PER_BATCH)
                .sleepBetweenBatch(Duration.TEN_SECONDS));
    }
}

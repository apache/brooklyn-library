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

/**
 * A trivially small "load" test, which just checks that our test is actually working.
 * It deploys just one app.
 */
public class LoadSanityTest extends AbstractLoadTest {

    @Test(groups="Integration")
    public void testApp() throws Exception {
        super.runLocalhostManyApps(new TestConfig(this)
                .execSshOnStart(true) // default is true, but be explicit
                .useSshMonitoring(true) // default is true, but be explicit
                .useHttpMonitoring(true) // default is true, but be explicit
                .useFunctionMonitoring(true) // default is true, but be explicit
                .totalApps(1));
    }
    
    @Test(groups="Integration")
    public void testAppExternallyMonitored() throws Exception {
        super.runLocalhostManyApps(new TestConfig(this)
                .simulateExternalMonitor(Predicates.instanceOf(VanillaSoftwareProcess.class), 5, Duration.ONE_SECOND)
                .useSshMonitoring(false)
                .useHttpMonitoring(false)
                .useFunctionMonitoring(false)
                .totalApps(1));
    }
}

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
package org.apache.brooklyn.entity.webapp.nodejs;

import static org.apache.brooklyn.entity.webapp.nodejs.NodeJsWebAppFixtureIntegrationTest.APP_FILE;
import static org.apache.brooklyn.entity.webapp.nodejs.NodeJsWebAppFixtureIntegrationTest.APP_NAME;
import static org.apache.brooklyn.entity.webapp.nodejs.NodeJsWebAppFixtureIntegrationTest.GIT_REPO_URL;
import static org.testng.Assert.assertNotNull;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.entity.AbstractEc2LiveTest;
import org.apache.brooklyn.entity.webapp.jboss.JBoss7Server;
import org.apache.brooklyn.util.http.HttpAsserts;
import org.testng.annotations.Test;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * A simple test of installing+running on AWS-EC2, using various OS distros and versions.
 */
public class NodeJsWebAppEc2LiveTest extends AbstractEc2LiveTest {

    @Override
    protected void doTest(Location loc) throws Exception {
        final NodeJsWebAppService server = app.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure("gitRepoUrl", GIT_REPO_URL)
                .configure("appFileName", APP_FILE)
                .configure("appName", APP_NAME));

        app.start(ImmutableList.of(loc));

        String url = server.getAttribute(NodeJsWebAppService.ROOT_URL);

        HttpAsserts.assertHttpStatusCodeEventuallyEquals(url, 200);
        HttpAsserts.assertContentContainsText(url, "Hello");
    }

    @Test(groups = {"Live", "Live-sanity"})
    @Override
    public void test_Ubuntu_12_0() throws Exception {
        super.test_Ubuntu_12_0();
    }

    @Test(groups = {"Live"})
    public void testWithOnlyPort22() throws Exception {
        // CentOS-6.3-x86_64-GA-EBS-02-85586466-5b6c-4495-b580-14f72b4bcf51-ami-bb9af1d2.1
        jcloudsLocation = mgmt.getLocationRegistry().getLocationManaged(LOCATION_SPEC, ImmutableMap.of(
                "tags", ImmutableList.of(getClass().getName()),
                "imageId", "us-east-1/ami-a96b01c0", 
                "hardwareId", SMALL_HARDWARE_ID));

        final NodeJsWebAppService server = app.createAndManageChild(EntitySpec.create(NodeJsWebAppService.class)
                .configure("gitRepoUrl", GIT_REPO_URL)
                .configure("appFileName", APP_FILE)
                .configure("appName", APP_NAME)
                .configure(JBoss7Server.USE_HTTP_MONITORING, false)
                .configure(JBoss7Server.OPEN_IPTABLES, true));
        
        app.start(ImmutableList.of(jcloudsLocation));
        
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);
        
        String url = server.getAttribute(NodeJsWebAppService.ROOT_URL);
        assertNotNull(url);
        
        assertViaSshLocalUrlListeningEventually(server, url);
    }
}

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
package org.apache.brooklyn.entity.database.postgresql;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.location.jclouds.JcloudsLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.testng.Assert.assertNotNull;

public class PostgreSqlInitializeDatabaseLiveTest extends BrooklynAppLiveTestSupport {

    public static final Logger log = LoggerFactory.getLogger(PostgreSqlIntegrationTest.class);
    JcloudsLocation jcloudsLocation;

    @Override
    @BeforeMethod(alwaysRun=true)
    public void setUp() throws Exception {
        super.setUp();

        jcloudsLocation = (JcloudsLocation) mgmt.getLocationRegistry().getLocationManaged("jclouds:aws-ec2:us-west-1", ImmutableMap.of(
                "osFamily", "centos",
                "osVersionRegex", "6\\..*"));
    }

    @Test(groups = "Live")
    public void testDatabaseInitialization() {
        PostgreSqlNode server = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(PostgreSqlNode.INITIALIZE_DB, Boolean.TRUE));

        runTest(server);
    }

    @Test(groups = "Live")
    public void testDatabaseInitializaationWithRoles() {
        PostgreSqlNode server = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(PostgreSqlNode.INITIALIZE_DB, Boolean.TRUE)
                .configure(PostgreSqlNode.ROLES.getName(), ImmutableMap.<String, Object>builder()
                        .put("Admin", ImmutableMap.of("properties", "SUPERUSER",
                                "privileges", "ALL PRIVILEGES ON ALL TABLES IN SCHEMA public"))
                        .put("Developer", ImmutableMap.of("privileges", ImmutableList.of(
                                "SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public", 
                                "EXECUTE ON ALL FUNCTIONS IN SCHEMA public")))
                        .put("Analyst", ImmutableMap.of("privileges", "SELECT ON ALL TABLES IN SCHEMA public"))
                        .build())
        );

        runTest(server);
    }

    protected void runTest(PostgreSqlNode server) {
        app.start(ImmutableList.of(jcloudsLocation));

        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_UP, true);
        EntityAsserts.assertAttributeEqualsEventually(server, Attributes.SERVICE_STATE_ACTUAL, Lifecycle.RUNNING);

        Integer port = server.getAttribute(PostgreSqlNode.POSTGRESQL_PORT);
        assertNotNull(port);
    }
}

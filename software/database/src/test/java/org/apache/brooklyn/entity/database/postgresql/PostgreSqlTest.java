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

import static org.testng.Assert.assertEquals;

import java.security.InvalidParameterException;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.LocationSpec;
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.test.BrooklynAppUnitTestSupport;
import org.apache.brooklyn.entity.software.base.SoftwareProcessDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.internal.ssh.RecordingSshTool;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.Test;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Predicates;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Unit tests for PostgresNode. It doesn't actually start Postgres; it just creates the entity
 * and driver so we can test individual methods.
 */
public class PostgreSqlTest extends BrooklynAppUnitTestSupport {

    public static final Logger log = LoggerFactory.getLogger(PostgreSqlTest.class);
    
    private SshMachineLocation stubbedMachine;
    
    public static class PostgreSqlNodeForTestingImpl extends PostgreSqlNodeImpl {
        @Override
        public SoftwareProcessDriver doInitDriver(MachineLocation machine) {
            return super.doInitDriver(machine);
        }
    }
    
    @Test
    public void testBuildCreateRolesWithNoPropertiesOrPrivileges() throws Exception {
        runBuildCreateRoles(
                MutableMap.<String, Map<String, ?>>of(
                        "Developer", null,
                        "Analyst", null),
                "\"CREATE ROLE Developer; "
                        + "CREATE ROLE Analyst; \"");
    }
    
    // Using example from description
    @Test
    public void testBuildCreateRolesQueryWithExampleInDescription() throws Exception {
        runBuildCreateRoles(
                ImmutableMap.<String, Map<String, ?>>of(
                        "Developer", ImmutableMap.<String, Object>of(
                                "properties", "CREATEDB LOGIN",
                                "privileges", ImmutableList.of(
                                        "SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public", 
                                        "EXECUTE ON ALL FUNCTIONS IN SCHEMA public")),
                        "Analyst", ImmutableMap.<String, Object>of(
                                "privileges", "SELECT ON ALL TABLES IN SCHEMA public")),
                "\"CREATE ROLE Developer WITH CREATEDB LOGIN; "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO Developer; "
                        + "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO Developer; "
                        + "CREATE ROLE Analyst; "
                        + "GRANT SELECT ON ALL TABLES IN SCHEMA public TO Analyst; \"");
    }
    
    // Using example from description
    @Test
    @SuppressWarnings("unchecked")
    public void testBuildCreateRolesQueryExtractingExampleFromDescription() throws Exception {
        String description = PostgreSqlNode.ROLES.getDescription();
        String yamlInDescription = description.substring(description.indexOf("Example:") + "Example:".length()).trim();
        Map<String, Map<String, ?>> roles = (Map<String, Map<String, ?>>) new Yaml().load(yamlInDescription);
        
        runBuildCreateRoles(
                roles,
                "\"CREATE ROLE Developer WITH CREATEDB LOGIN; "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO Developer; "
                        + "GRANT EXECUTE ON ALL FUNCTIONS IN SCHEMA public TO Developer; "
                        + "CREATE ROLE Analyst; "
                        + "GRANT SELECT ON ALL TABLES IN SCHEMA public TO Analyst; \"");
    }
    
    @Test
    public void testBuildCreateRolesQueryFailsOnInsecureRoleName() throws Exception {
        runBuildCreateRolesExpectingFailure(
                MutableMap.<String, Map<String, ?>>of(
                        "Robert'); DROP TABLE students;--,", null),
                InvalidParameterException.class,
                "Query input seems to be insecure");
    }
    
    @Test
    public void testBuildCreateRolesQueryFailsOnInsecureProperty() throws Exception {
        runBuildCreateRolesExpectingFailure(
                MutableMap.<String, Map<String, ?>>of(
                        "Developer", ImmutableMap.<String, Object>of(
                                "properties", "Robert'); DROP TABLE students;--,")),
                InvalidParameterException.class,
                "Query input seems to be insecure");
    }
    
    @Test
    public void testBuildCreateRolesQueryFailsOnInsecurePrivilege() throws Exception {
        runBuildCreateRolesExpectingFailure(
                MutableMap.<String, Map<String, ?>>of(
                        "Developer", ImmutableMap.<String, Object>of(
                                "privileges", "Robert'); DROP TABLE students;--,")),
                InvalidParameterException.class,
                "Query input seems to be insecure");
    }
    
    protected void runBuildCreateRoles(Map<String, Map<String, ?>> roles, String expected) throws Exception {
        stubbedMachine = app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("sshToolClass", RecordingSshTool.class.getName())
                .configure("address", "1.2.3.4"));
        
        PostgreSqlNode pgsql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .impl(PostgreSqlNodeForTestingImpl.class)
                .configure(PostgreSqlNode.ROLES, roles)
                .location(stubbedMachine));
        
        PostgreSqlNodeForTestingImpl impl = (PostgreSqlNodeForTestingImpl) Entities.deproxy(pgsql);
        PostgreSqlSshDriver driver = (PostgreSqlSshDriver) impl.doInitDriver(stubbedMachine);

        String query = driver.buildCreateRolesQuery();
        assertEquals(query, expected);
    }
    
    protected void runBuildCreateRolesExpectingFailure(Map<String, Map<String, ?>> roles, Class<? extends Exception> expectedException, String phraseToContain) throws Exception {
        stubbedMachine = app.getManagementContext().getLocationManager().createLocation(LocationSpec.create(SshMachineLocation.class)
                .configure("sshToolClass", RecordingSshTool.class.getName())
                .configure("address", "1.2.3.4"));
        
        PostgreSqlNode pgsql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .impl(PostgreSqlNodeForTestingImpl.class)
                .configure(PostgreSqlNode.ROLES, roles)
                .location(stubbedMachine));
        
        PostgreSqlNodeForTestingImpl impl = (PostgreSqlNodeForTestingImpl) Entities.deproxy(pgsql);
        PostgreSqlSshDriver driver = (PostgreSqlSshDriver) impl.doInitDriver(stubbedMachine);

        try {
            String query = driver.buildCreateRolesQuery();
            Asserts.shouldHaveFailedPreviously();
        } catch (Exception e) {
            Throwable e2 = Exceptions.getFirstThrowableMatching(e, Predicates.instanceOf(expectedException));
            if (e2 == null) throw e;
            Asserts.expectedFailureContains(e2, phraseToContain);
        }
    }
}

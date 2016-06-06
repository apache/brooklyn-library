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

import static org.apache.brooklyn.test.Asserts.assertEquals;
import static org.apache.brooklyn.test.Asserts.assertTrue;
import static org.apache.brooklyn.test.Asserts.fail;

import java.util.List;
import java.util.Map;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.Location;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.core.test.BrooklynAppLiveTestSupport;
import org.apache.brooklyn.entity.database.DatastoreMixins.DatastoreCommon;
import org.apache.brooklyn.entity.database.VogellaExampleAccess;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;

/**
 * Runs the popular Vogella MySQL tutorial against PostgreSQL
 * from
 * http://www.vogella.de/articles/MySQLJava/article.html
 */
public class PostgreSqlIntegrationTest extends BrooklynAppLiveTestSupport {

    public static final Logger log = LoggerFactory.getLogger(PostgreSqlIntegrationTest.class);
    
    //from http://www.vogella.de/articles/MySQLJava/article.html
    public static final String CREATION_SCRIPT =
            "CREATE USER sqluser WITH PASSWORD 'sqluserpw';\n" +
            "CREATE DATABASE feedback OWNER sqluser;\n" +
            "\\c feedback;\n" +
            "CREATE TABLE COMMENTS ( " +
                    "id INT8 NOT NULL,  " +
                    "MYUSER VARCHAR(30) NOT NULL, " +
                    "EMAIL VARCHAR(30),  " +
                    "WEBPAGE VARCHAR(100) NOT NULL,  " +
                    "DATUM DATE NOT NULL,  " +
                    "SUMMARY VARCHAR(40) NOT NULL, " +
                    "COMMENTS VARCHAR(400) NOT NULL, " +
                    "PRIMARY KEY (ID) " +
                ");\n" +
            "GRANT ALL ON comments TO sqluser;\n" +
            "INSERT INTO COMMENTS values (1, 'lars', 'myemail@gmail.com','http://www.vogella.de', '2009-09-14 10:33:11', 'Summary','My first comment' );";

    private Location loc;

    @BeforeMethod(alwaysRun=true)
    @Override
    public void setUp() throws Exception {
        super.setUp();
        loc = app.newLocalhostProvisioningLocation();
    }
    
    @Test(groups = "Integration")
    public void test_localhost() throws Exception {
        PostgreSqlNode pgsql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure(PostgreSqlNode.MAX_CONNECTIONS, 10)
                .configure(PostgreSqlNode.SHARED_MEMORY, "512kB")); // Very low so kernel configuration not needed

        app.start(ImmutableList.of(loc));
        String url = pgsql.getAttribute(DatastoreCommon.DATASTORE_URL);
        log.info("PostgreSql started on "+url);
        new VogellaExampleAccess("org.postgresql.Driver", url).readModifyAndRevertDataBase();
        log.info("Ran vogella PostgreSql example -- SUCCESS");
    }

    @Test(groups = "Integration")
    public void test_localhost_initialisingDb() throws Exception {
        PostgreSqlNode pgsql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(DatastoreCommon.CREATION_SCRIPT_CONTENTS, CREATION_SCRIPT)
                .configure(PostgreSqlNode.MAX_CONNECTIONS, 10)
                .configure(PostgreSqlNode.SHARED_MEMORY, "512kB") // Very low so kernel configuration not needed
                .configure(PostgreSqlNode.INITIALIZE_DB, true)
                ); 

        app.start(ImmutableList.of(loc));
        String url = pgsql.getAttribute(DatastoreCommon.DATASTORE_URL);
        log.info("PostgreSql started on "+url);
        new VogellaExampleAccess("org.postgresql.Driver", url).readModifyAndRevertDataBase();
        log.info("Ran vogella PostgreSql example -- SUCCESS");
    }

    // Note we don't use "SELECT ON DATABASE postgres" because that gives an error, as described
    // in http://stackoverflow.com/a/8247052/1393883
    @Test(groups = "Integration")
    public void test_localhost_withRoles() throws Exception {
        PostgreSqlNode pgsql = app.createAndManageChild(EntitySpec.create(PostgreSqlNode.class)
                .configure(PostgreSqlNode.MAX_CONNECTIONS, 10)
                .configure(PostgreSqlNode.SHARED_MEMORY, "512kB") // Very low so kernel configuration not needed
                .configure(PostgreSqlNode.INITIALIZE_DB, true)
                .configure(PostgreSqlNode.ROLES, ImmutableMap.<String, Map<String, ?>>of(
                        "Developer", ImmutableMap.<String, Object>of(
                                "properties", "CREATEDB LOGIN",
                                "privileges", ImmutableList.of(
                                        "SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public", 
                                        "EXECUTE ON ALL FUNCTIONS IN SCHEMA public")),
                        "Analyst", ImmutableMap.<String, Object>of(
                                "properties", "LOGIN",
                                "privileges", "SELECT ON ALL TABLES IN SCHEMA public")))
                ); 

        app.start(ImmutableList.of(loc));
        String url = pgsql.getAttribute(DatastoreCommon.DATASTORE_URL);
        log.info("PostgreSql started on "+url);
        
        // Use psql to get the roles and properties
        // TODO Does not list privileges, so that is not tested.
        SshMachineLocation machine = Machines.findUniqueMachineLocation(pgsql.getLocations(), SshMachineLocation.class).get();
        String installDir = pgsql.sensors().get(PostgreSqlNode.INSTALL_DIR);
        int port = pgsql.sensors().get(PostgreSqlNode.POSTGRESQL_PORT);
        ByteArrayOutputStream stdoutStream = new ByteArrayOutputStream();
        ByteArrayOutputStream stderrStream = new ByteArrayOutputStream();
        int result = machine.execCommands(
                ImmutableMap.of(
                        SshMachineLocation.STDOUT.getName(), stdoutStream,
                        SshMachineLocation.STDERR.getName(), stderrStream),
                "checkState", 
                ImmutableList.of(BashCommands.sudoAsUser("postgres", installDir + "/bin/psql -p " + port + " --command \"\\du\"")));
        String stdout = new String(stdoutStream.toByteArray());
        String stderr = new String(stderrStream.toByteArray());
        assertEquals(result, 0, "stdout="+stdout+"; stderr="+stderr);
        checkRole(stdout, "analyst", "");
        checkRole(stdout, "developer", "Create DB");
    }
    
    private void checkRole(String du, String role, String attrib) {
        List<String> lines = ImmutableList.copyOf(Splitter.on("\n").trimResults().split(du));
        boolean found = false;
        for (String line : lines) {
            if (line.startsWith(role)) {
                assertTrue(line.matches(".*"+attrib+".*"), "du="+du);
                found = true;
                break;
            }
        }
        if (!found) {
            fail("Role "+role+" not found in du="+du);
        }
        du.split("\n");
    }
}

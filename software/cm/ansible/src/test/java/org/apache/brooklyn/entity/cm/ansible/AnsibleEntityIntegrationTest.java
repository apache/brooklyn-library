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
package org.apache.brooklyn.entity.cm.ansible;

import org.apache.brooklyn.api.entity.EntitySpec;
import org.apache.brooklyn.api.location.MachineDetails;
import org.apache.brooklyn.api.location.OsDetails;
import org.apache.brooklyn.api.mgmt.Task;
import org.apache.brooklyn.core.entity.Entities;
import org.apache.brooklyn.core.entity.EntityAsserts;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.BasicMachineDetails;
import org.apache.brooklyn.core.test.entity.TestApplication;

import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;
import org.apache.brooklyn.location.localhost.LocalhostMachineProvisioningLocation;
import org.apache.brooklyn.location.ssh.SshMachineLocation;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;

public class AnsibleEntityIntegrationTest {
    protected TestApplication app;
    protected LocalhostMachineProvisioningLocation testLocation;
    protected AnsibleEntity ansible;
    protected SshMachineLocation sshHost;

    String playbookYaml = Joiner.on("\n").join(
            "---",
            "- hosts: localhost",
            "  sudo: True",
            "",
            "  tasks:",
            "  - apt: name=apache2 state=latest",
            "    when: ansible_distribution == 'Debian' or ansible_distribution == 'Ubuntu'",
            "",
            "  - yum: name=httpd state=latest",
            "    when: ansible_distribution == 'CentOS' or ansible_distribution == 'Red Hat Enterprise Linux'",
            "",
            "  - service: name=apache2 state=started enabled=no",
            "    when: ansible_distribution == 'Debian' or ansible_distribution == 'Ubuntu'",
            "",
            "  - service: name=httpd state=started enabled=no",
            "    when: ansible_distribution == 'CentOS' or ansible_distribution == 'Red Hat Enterprise Linux'");

    @BeforeMethod(alwaysRun = true)
    public void setup() throws Exception {
        app = TestApplication.Factory.newManagedInstanceForTests();;
        testLocation = new LocalhostMachineProvisioningLocation();
        sshHost = testLocation.obtain();
    }

    @AfterMethod(alwaysRun = true)
    public void shutdown() {
        Entities.destroyAll(app.getManagementContext());
    }
    
    @Test(groups = {"Integration"})
    public void testAnsible() {
        Task<BasicMachineDetails> detailsTask = app.getExecutionContext().submit(
                BasicMachineDetails.taskForSshMachineLocation(sshHost));
        MachineDetails machine = detailsTask.getUnchecked();


        OsDetails details = machine.getOsDetails();
        
        String osName = details.getName();
        String playbookServiceName = getPlaybookServiceName(osName);
        ansible = app.createAndManageChild(EntitySpec.create(AnsibleEntity.class)
                .configure("playbook.yaml", playbookYaml)
                .configure("playbook", playbookServiceName)
                .configure("service.name", playbookServiceName));

        app.start(ImmutableList.of(testLocation));
        EntityAsserts.assertAttributeEqualsEventually(ansible, Startable.SERVICE_UP, true);

        ansible.stop();
        EntityAsserts.assertAttributeEqualsEventually(ansible, Startable.SERVICE_UP, false);
    }

    private String getPlaybookServiceName(String os) {
        String name;

        switch (os.toLowerCase()) {
            case "fedora":
                name = "httpd";
                break;
            case "centos":
                name = "httpd";
                break;
            default:
                name = "apache2";
        }
        return name;
    }
}



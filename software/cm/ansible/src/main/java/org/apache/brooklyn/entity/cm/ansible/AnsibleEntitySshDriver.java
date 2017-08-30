/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.brooklyn.entity.cm.ansible;

import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessSshDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.text.Strings;

public class AnsibleEntitySshDriver extends AbstractSoftwareProcessSshDriver implements AnsibleEntityDriver {
    public AnsibleEntitySshDriver(AnsibleEntityImpl entity, SshMachineLocation machine) {
        super(entity, machine);
    }

    @Override
    public boolean isRunning() {
        return newScript(MutableMap.of("usePidFile", false), CHECK_RUNNING)
                .body.append(getStatusCmd())
                .execute() == 0;
    }

    @Override
    public void stop() {
        final String serviceName = getEntity().config().get(AnsibleConfig.SERVICE_NAME);

        newScript(MutableMap.of("usePidFile", false), STOPPING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(sudo(String.format(getEntity().config().get(AnsibleConfig.ANSIBLE_SERVICE_STOP), serviceName)))
                .execute();
    }

    @Override
    public void install() {
        Object extraVars = getEntity().config().get(AnsibleConfig.ANSIBLE_VARS);
        String playbookName = getEntity().config().get(AnsibleConfig.ANSIBLE_PLAYBOOK);
        String playbookUrl = getEntity().config().get(AnsibleConfig.ANSIBLE_PLAYBOOK_URL);
        String playbookYaml = getEntity().config().get(AnsibleConfig.ANSIBLE_PLAYBOOK_YAML);

        if (playbookUrl != null && playbookYaml != null) {
            throw new IllegalArgumentException( "You can not specify both "+  AnsibleConfig.ANSIBLE_PLAYBOOK_URL.getName() +
                    " and " + AnsibleConfig.ANSIBLE_PLAYBOOK_YAML.getName() + " as arguments.");
        }

        if (playbookUrl == null && playbookYaml == null) {
            throw new IllegalArgumentException("You have to specify either " + AnsibleConfig.ANSIBLE_PLAYBOOK_URL.getName() +
                    " or " + AnsibleConfig.ANSIBLE_PLAYBOOK_YAML.getName() + " as arguments.");
        }

        DynamicTasks.queue(AnsiblePlaybookTasks.installAnsible(getInstallDir(), false));
        DynamicTasks.queue(AnsiblePlaybookTasks.setUpHostsFile(false));

        if (extraVars != null) {
            DynamicTasks.queue(AnsiblePlaybookTasks.configureExtraVars(getRunDir(), extraVars, false));
        }

        if (Strings.isNonBlank(playbookUrl)) {
            DynamicTasks.queue(AnsiblePlaybookTasks.installPlaybook(getRunDir(), playbookName, playbookUrl));
        }

        if (Strings.isNonBlank(playbookYaml)) {
            DynamicTasks.queue(AnsiblePlaybookTasks.buildPlaybookFile(getRunDir(), playbookName));
        }
        DynamicTasks.queue(AnsiblePlaybookTasks.runAnsible(getRunDir(), extraVars, playbookName));
    }

    @Override
    public void customize() {
        newScript(CUSTOMIZING).execute();
    }

    @Override
    public void launch() {
        final String serviceName = getEntity().config().get(AnsibleConfig.SERVICE_NAME);

        newScript(MutableMap.of("usePidFile", false), LAUNCHING)
                .updateTaskAndFailOnNonZeroResultCode()
                .body.append(sudo(String.format(getEntity().config().get(AnsibleConfig.ANSIBLE_SERVICE_START), serviceName)))
                .execute();
    }

    @Override
    public String getStatusCmd() {
        String serviceNameCheck = getEntity().config().get(AnsibleConfig.SERVICE_NAME).replaceFirst("^(.)(.*)", "[$1]$2");
        String statusCmd = String.format("ps -ef | grep %s", serviceNameCheck);

        Integer serviceCheckPort = getEntity().config().get(AnsibleConfig.ANSIBLE_SERVICE_CHECK_PORT);

        if (serviceCheckPort != null) {
            statusCmd = sudo(String.format("ansible localhost -c local -m wait_for -a \"host=" +
                    getEntity().config().get(AnsibleConfig.ANSIBLE_SERVICE_CHECK_HOST) +
                    "\" port=%d\"", serviceCheckPort));
        }

        return statusCmd;
    }

    @Override
    public ProcessTaskWrapper<Integer> ansibleCommand(String module, String args) {
        return DynamicTasks.queue(
                AnsiblePlaybookTasks.moduleCommand(module, getEntity().config().get(AnsibleConfig.ANSIBLE_VARS), getRunDir(), args));
    }
}

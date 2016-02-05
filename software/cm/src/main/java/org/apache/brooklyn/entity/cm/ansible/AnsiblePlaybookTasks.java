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

import org.apache.brooklyn.api.entity.Entity;

import org.apache.brooklyn.api.mgmt.TaskFactory;
import org.apache.brooklyn.core.effector.EffectorTasks;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.util.core.ResourceUtils;
import org.apache.brooklyn.util.core.task.Tasks;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsiblePlaybookTasks {
    private static final Logger LOG = LoggerFactory.getLogger(AnsiblePlaybookTasks.class);

    public static TaskFactory<?> installAnsible(String ansibleDirectory, boolean force) {
        String installCmd = cdAndRun(ansibleDirectory, AnsibleBashCommands.INSTALL_ANSIBLE);
        if (!force) installCmd = BashCommands.alternatives("which ansible", installCmd);
        return SshEffectorTasks.ssh(installCmd).summary("install ansible");
    }

    public static TaskFactory<?> installPlaybook(final String ansibleDirectory, final String playbookName, final String playbookUrl) {
        return Tasks.sequential("build ansible playbook file for "+playbookName,
                SshEffectorTasks.put(ansibleDirectory + "/" + playbookName + ".yaml").contents(ResourceUtils.create().getResourceFromUrl(playbookUrl)).createDirectory());
    }
    
    protected static String cdAndRun(String targetDirectory, String command) {
        return BashCommands.chain("mkdir -p "+targetDirectory,
                "cd "+targetDirectory,
                command);
    }

    public static TaskFactory<?> buildPlaybookFile(final String ansibleDirectory, String playbook) {
        Entity entity = EffectorTasks.findEntity();
        String yaml = entity.config().get(AnsibleConfig.ANSIBLE_PLAYBOOK_YAML);

        return Tasks.sequential("build ansible playbook file for "+ playbook,
                    SshEffectorTasks.put(Urls.mergePaths(ansibleDirectory) + "/" + playbook + ".yaml").contents(yaml).createDirectory());
    }

    public static TaskFactory<?> runAnsible(final String ansibleDirectory, String playbookName) {
        String cmd = String.format("sudo ansible-playbook -i \"localhost,\" -c local -s %s.yaml", playbookName);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Ansible command: {}", cmd);
        }

        return SshEffectorTasks.ssh(cdAndRun(ansibleDirectory, cmd)).
                summary("run ansible playbook for " + playbookName).requiringExitCodeZero();
    }
    
}

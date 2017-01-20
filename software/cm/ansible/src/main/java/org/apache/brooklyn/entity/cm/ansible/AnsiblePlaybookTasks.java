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
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.ssh.BashCommands;
import org.apache.brooklyn.util.text.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import static org.apache.brooklyn.core.effector.ssh.SshEffectorTasks.ssh;
import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

public class AnsiblePlaybookTasks {
    private static final Logger LOG = LoggerFactory.getLogger(AnsiblePlaybookTasks.class);
    private static final String EXTRA_VARS_FILENAME = "extra_vars.yaml";

    public static TaskFactory<?> installAnsible(String ansibleDirectory, boolean force) {
        String installCmd = cdAndRun(ansibleDirectory, AnsibleBashCommands.INSTALL_ANSIBLE);
        if (!force) installCmd = BashCommands.alternatives("which ansible", installCmd);
        return ssh(installCmd).summary("install ansible");
    }

    public static TaskFactory<?> installPlaybook(final String ansibleDirectory, final String playbookName, final String playbookUrl) {
        return Tasks.sequential("build ansible playbook file for "+playbookName,
                SshEffectorTasks.put(ansibleDirectory + "/" + playbookName + ".yaml")
                    .contents(ResourceUtils.create().getResourceFromUrl(playbookUrl))
                    .createDirectory());
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
            SshEffectorTasks.put(Urls.mergePaths(ansibleDirectory) + "/" + playbook + ".yaml")
                .contents(yaml).createDirectory());
    }

    public static TaskFactory<?> runAnsible(final String dir, Object extraVars, String playbookName) {
        String cmd = sudo(String.format("ansible-playbook "
            + optionalExtraVarsParameter(extraVars)
            + " -s %s.yaml", playbookName));

        if (LOG.isDebugEnabled()) {
            LOG.debug("Ansible command: {}", cmd);
        }

        return ssh(cdAndRun(dir, cmd)).
                summary("run ansible playbook for " + playbookName).requiringExitCodeZero();
    }

    public static ProcessTaskFactory<Integer> moduleCommand(String module, Object extraVars, String root, String args) {
        final String command = "ansible localhost "
            + optionalExtraVarsParameter(extraVars)
            + " -m '" + module + "' -a '" + args + "'";
        return ssh(sudo(BashCommands.chain("cd " + root, command)))
            .summary("ad-hoc: " + command).requiringExitCodeZero();
    }

    public static TaskFactory<?> configureExtraVars(String dir, Object extraVars, boolean force) {
        DumperOptions options = new DumperOptions();
        options.setDefaultScalarStyle(DumperOptions.ScalarStyle.DOUBLE_QUOTED);
        Yaml asYaml = new Yaml(options);
        final String varsYaml = asYaml.dump(extraVars);
        return SshEffectorTasks.put(Urls.mergePaths(dir, EXTRA_VARS_FILENAME))
            .contents(varsYaml)
            .summary("install extra vars")
            .createDirectory();
    }

    private static String optionalExtraVarsParameter(Object extraVars) {
        if (null == extraVars || Strings.isBlank(extraVars.toString())) {
            return "";
        }
        return " --extra-vars \"@" + EXTRA_VARS_FILENAME + "\" ";
    }

    public static TaskFactory<?> setUpHostsFile(boolean force) {
        String checkInstalled = !force ? "grep localhost.ansible_connection=local /etc/ansible/hosts || " : "";
        return ssh(checkInstalled + sudo("echo 'localhost ansible_connection=local' | sudo tee /etc/ansible/hosts"))
            .requiringExitCodeZero()
            .summary("write hosts file");
    }

}


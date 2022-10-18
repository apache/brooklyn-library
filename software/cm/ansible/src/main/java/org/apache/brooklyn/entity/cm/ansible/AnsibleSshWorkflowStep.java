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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.brooklyn.api.entity.drivers.DriverDependentEntity;
import org.apache.brooklyn.api.entity.drivers.EntityDriver;
import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.resolve.jackson.BeanWithTypeUtils;
import org.apache.brooklyn.core.resolve.jackson.BrooklynJacksonSerializationUtils;
import org.apache.brooklyn.core.workflow.WorkflowStepDefinition;
import org.apache.brooklyn.core.workflow.WorkflowStepInstanceExecutionContext;
import org.apache.brooklyn.core.workflow.steps.SshWorkflowStep;
import org.apache.brooklyn.entity.software.base.AbstractSoftwareProcessDriver;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.core.task.system.ProcessTaskFactory;
import org.apache.brooklyn.util.exceptions.Exceptions;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.yaml.Yamls;

import java.util.Map;

public class AnsibleSshWorkflowStep extends WorkflowStepDefinition {

    public static final String SHORTHAND = "${playbook_url} [ \" named \" ${playbook_name} ]";

    public static final ConfigKey<String> RUN_DIR = ConfigKeys.newStringConfigKey("run_dir");
    public static final ConfigKey<String> INSTALL_DIR = ConfigKeys.newStringConfigKey("install_dir");

    public static final ConfigKey<Boolean> INSTALL_ANSIBLE = ConfigKeys.newBooleanConfigKey("install");

    public static final ConfigKey<String> ANSIBLE_PLAYBOOK = ConfigKeys.newStringConfigKey("playbook_name", "Local filename to use when installing the playbook");
    public static final ConfigKey<Object> ANSIBLE_PLAYBOOK_YAML = ConfigKeys.newConfigKey(Object.class, "playbook_yaml");
    public static final ConfigKey<String> ANSIBLE_PLAYBOOK_URL = ConfigKeys.newStringConfigKey("playbook_url");
    public static final ConfigKey<Object> ANSIBLE_VARS = ConfigKeys.newConfigKey(Object.class, "vars");

    @Override
    public void populateFromShorthand(String expression) {
        populateFromShorthandTemplate(SHORTHAND, expression);
    }

    @Override
    protected Object doTaskBody(WorkflowStepInstanceExecutionContext context) {
        SshMachineLocation machine = Locations.findUniqueSshMachineLocation(context.getEntity().getLocations()).orThrow("No SSH location available for workflow at " + context.getEntity());

        Object extraVars = context.getInput(ANSIBLE_VARS);
        String playbookName = context.getInput(ANSIBLE_PLAYBOOK);

        String playbookUrl = context.getInput(ANSIBLE_PLAYBOOK_URL);
        Object playbookYamlO = context.getInput(ANSIBLE_PLAYBOOK_YAML);

        if (playbookUrl != null && playbookYamlO != null) {
            throw new IllegalArgumentException( "You cannot specify both "+  AnsibleConfig.ANSIBLE_PLAYBOOK_URL.getName() +
                    " and " + AnsibleConfig.ANSIBLE_PLAYBOOK_YAML.getName() + " as arguments.");
        }

        if (playbookUrl == null && playbookYamlO == null) {
            throw new IllegalArgumentException("You have to specify either " + AnsibleConfig.ANSIBLE_PLAYBOOK_URL.getName() +
                    " or " + AnsibleConfig.ANSIBLE_PLAYBOOK_YAML.getName() + " as arguments.");
        }

        String playbookYaml;
        try {
            playbookYaml = playbookYamlO==null ? null : playbookYamlO instanceof String ? (String) playbookYamlO :
                    BeanWithTypeUtils.newYamlMapper(context.getManagementContext(), false, null, false).writeValueAsString(playbookYamlO);
        } catch (JsonProcessingException e) {
            throw Exceptions.propagateAnnotated("Invalid YAML supplied for playbook", e);
        }
        if (playbookName==null) playbookName = "playbook-"+Strings.firstNonNull(playbookUrl, playbookYaml).hashCode();

        if (!Boolean.FALSE.equals(context.getInput(INSTALL_ANSIBLE))) {
            DynamicTasks.queue(AnsiblePlaybookTasks.installAnsible(getInstallDir(context), false));
            DynamicTasks.queue(AnsiblePlaybookTasks.setUpHostsFile(false));
        }

        if (extraVars != null) {
            DynamicTasks.queue(AnsiblePlaybookTasks.configureExtraVars(getRunDir(context), extraVars, false));
        }

        if (Strings.isNonBlank(playbookUrl)) {
            DynamicTasks.queue(AnsiblePlaybookTasks.installPlaybook(getRunDir(context), playbookName, playbookUrl));
        }

        if (Strings.isNonBlank(playbookYaml)) {
            DynamicTasks.queue(AnsiblePlaybookTasks.buildPlaybookFile(getRunDir(context), playbookName, playbookYaml));
        }

        ProcessTaskFactory<Map<?,?>> tf = SshWorkflowStep.customizeProcessTaskFactory(context, AnsiblePlaybookTasks.runAnsible(getRunDir(context), extraVars, playbookName));
        return DynamicTasks.queue(tf).asTask().getUnchecked();
    }

    private String getRunDir(WorkflowStepInstanceExecutionContext context) {
        String candidate = context.getInput(RUN_DIR);
        if (candidate!=null) return candidate;

        if (context.getEntity() instanceof DriverDependentEntity) {
            EntityDriver driver = ((DriverDependentEntity) context.getEntity()).getDriver();
            if (driver instanceof AbstractSoftwareProcessDriver) {
                return ((AbstractSoftwareProcessDriver)driver).getRunDir();
            }
        }

        return "./brooklyn-managed-ansible/install/";
    }

    private String getInstallDir(WorkflowStepInstanceExecutionContext context) {
        String candidate = context.getInput(INSTALL_DIR);
        if (candidate!=null) return candidate;

        if (context.getEntity() instanceof DriverDependentEntity) {
            EntityDriver driver = ((DriverDependentEntity) context.getEntity()).getDriver();
            if (driver instanceof AbstractSoftwareProcessDriver) {
                return ((AbstractSoftwareProcessDriver)driver).getInstallDir();
            }
        }

        return "./brooklyn-managed-ansible/run-"+context.getEntity().getApplicationId()+"-"+context.getEntity().getId()+"/";
    }

}

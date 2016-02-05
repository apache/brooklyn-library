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
import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.core.effector.ssh.SshEffectorTasks;
import org.apache.brooklyn.core.entity.Attributes;
import org.apache.brooklyn.core.entity.lifecycle.Lifecycle;
import org.apache.brooklyn.core.entity.trait.Startable;
import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.core.location.Machines;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;
import org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.brooklyn.util.core.task.DynamicTasks;
import org.apache.brooklyn.util.guava.Maybe;
import org.apache.brooklyn.util.net.Urls;
import org.apache.brooklyn.util.text.Strings;
import org.apache.brooklyn.util.time.Duration;
import org.apache.brooklyn.util.time.Time;

import com.google.common.base.Supplier;

public class AnsibleLifecycleEffectorTasks extends MachineLifecycleEffectorTasks implements AnsibleConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AnsibleLifecycleEffectorTasks.class);

    protected String serviceName;
    protected SshFeed serviceSshFeed;
    
    public AnsibleLifecycleEffectorTasks() {
    }

    public String getServiceName() {
        if (serviceName!=null) return serviceName;
        return serviceName = entity().config().get(AnsibleConfig.SERVICE_NAME);
    }

    @Override
    public void attachLifecycleEffectors(Entity entity) {
        if (getServiceName()==null && getClass().equals(AnsibleLifecycleEffectorTasks.class)) {
            // warn on incorrect usage
            LOG.warn("Uses of "+getClass()+" must define a PID file or a service name (or subclass and override {start,stop} methods as per javadoc) " +
                    "in order for check-running and stop to work");
        }
        super.attachLifecycleEffectors(entity);
    }

    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        startWithAnsibleAsync();

        return "ansible start tasks submitted";
    }

    protected String getPlaybookName() {
        return entity().config().get(ANSIBLE_PLAYBOOK);
    }

    protected void startWithAnsibleAsync() {
        String baseDir = MachineLifecycleEffectorTasks.resolveOnBoxDir(entity(), Machines.findUniqueMachineLocation(entity().getLocations(), SshMachineLocation.class).get());
        String installDir = Urls.mergePaths(baseDir, "installs/ansible");

        String playbookUrl = entity().config().get(ANSIBLE_PLAYBOOK_URL);
        String playbookYaml = entity().config().get(ANSIBLE_PLAYBOOK_YAML);

        if (Strings.isNonBlank(playbookUrl) && Strings.isNonBlank(playbookYaml)) {
            throw new IllegalArgumentException("You can specify " +  AnsibleConfig.ANSIBLE_PLAYBOOK_URL.getName() +  " or " + AnsibleConfig.ANSIBLE_PLAYBOOK_YAML.getName() + " but not both of them!");
        }

        DynamicTasks.queue(AnsiblePlaybookTasks.installAnsible(installDir, false));

        String runDir = Urls.mergePaths(baseDir, "apps/"+entity().getApplicationId()+"/ansible/playbooks/"+entity().getEntityType().getSimpleName()+"_"+entity().getId());
        
        if (Strings.isNonBlank(playbookUrl)) {
            DynamicTasks.queue(AnsiblePlaybookTasks.installPlaybook(runDir, getPlaybookName(), playbookUrl));
        }

        if (Strings.isNonBlank(playbookYaml)) {
            DynamicTasks.queue(AnsiblePlaybookTasks.buildPlaybookFile(runDir, getPlaybookName()));
        }
        DynamicTasks.queue(AnsiblePlaybookTasks.runAnsible(runDir, getPlaybookName()));
    }

    protected void postStartCustom() {
        boolean result = false;
        result |= tryCheckStartService();

        if (!result) {
            LOG.warn("No way to check whether "+entity()+" is running; assuming yes");
        }
        entity().sensors().set(SoftwareProcess.SERVICE_UP, true);
        
        Maybe<SshMachineLocation> machine = Locations.findUniqueSshMachineLocation(entity().getLocations());

        if (machine.isPresent()) {
            
            String serviceName = String.format("[%s]%s", entity().config().get(SERVICE_NAME).substring(0, 1),
                    entity().config().get(SERVICE_NAME).substring(1));
            String checkCmd = String.format("ps -ef | grep %s", serviceName);

            Integer serviceCheckPort = entity().config().get(ANSIBLE_SERVICE_CHECK_PORT);

            if (serviceCheckPort != null) {
                checkCmd = String.format("sudo ansible localhost -c local -m wait_for -a \"host=0.0.0.0 port=%d\"", serviceCheckPort);
            }
            serviceSshFeed = SshFeed.builder()
                    .entity(entity())
                    .period(Duration.ONE_MINUTE)
                    .machine(machine.get())
                    .poll(new SshPollConfig<Boolean>(Startable.SERVICE_UP)
                            .command(checkCmd)
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
                    
             entity().feeds().addFeed(serviceSshFeed);
        } else {
            LOG.warn("Location(s) {} not an ssh-machine location, so not polling for status; setting serviceUp immediately", entity().getLocations());
        }
    }

    protected boolean tryCheckStartService() {
        if (getServiceName()==null) return false;

        // if it's still up after 5s assume we are good (default behaviour)
        Time.sleep(Duration.FIVE_SECONDS);
        if (!((Integer)0).equals(DynamicTasks.queue(SshEffectorTasks.ssh(String.format(entity().config().get(AnsibleConfig.ANSIBLE_SERVICE_START), getServiceName()))).get())) {
            throw new IllegalStateException("The process for "+entity()+" appears not to be running (service "+getServiceName()+")");
        }

        return true;
    }

    @Override
    protected String stopProcessesAtMachine() {
        boolean result = false;
        result |= tryStopService();
        if (!result) {
            throw new IllegalStateException("The process for "+entity()+" could not be stopped (no impl!)");
        }
        return "stopped";
    }

    @Override
    protected StopMachineDetails<Integer> stopAnyProvisionedMachines() {
        return super.stopAnyProvisionedMachines();
    }

    protected boolean tryStopService() {
        if (getServiceName()==null) return false;
        int result = DynamicTasks.queue(SshEffectorTasks.ssh(String.format(entity().config().get(AnsibleConfig.ANSIBLE_SERVICE_STOP), getServiceName()))).get();
        if (0 == result) return true;
        if (entity().getAttribute(Attributes.SERVICE_STATE_ACTUAL) != Lifecycle.RUNNING)
            return true;
        
        throw new IllegalStateException("The process for "+entity()+" appears could not be stopped (exit code "+result+" to service stop)");
    }
}

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

import org.apache.brooklyn.core.location.Locations;
import org.apache.brooklyn.entity.software.base.SoftwareProcessImpl;
import org.apache.brooklyn.feed.ssh.SshFeed;
import org.apache.brooklyn.feed.ssh.SshPollConfig;
import org.apache.brooklyn.location.ssh.SshMachineLocation;
import org.apache.brooklyn.util.core.task.system.ProcessTaskWrapper;
import org.apache.brooklyn.util.guava.Maybe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AnsibleEntityImpl extends SoftwareProcessImpl implements AnsibleEntity {

    private static final Logger LOG = LoggerFactory.getLogger(AnsibleEntityImpl.class);

    private SshFeed feed;

    @Override
    public Class getDriverInterface() {
        return AnsibleEntityDriver.class;
    }

    @Override
    public AnsibleEntityDriver getDriver() {
        return (AnsibleEntityDriver) super.getDriver();
    }

    @Override
    protected void connectSensors() {
        super.connectSensors();

        Maybe<SshMachineLocation> machine = Locations.findUniqueSshMachineLocation(getLocations());

        if (machine.isPresent()) {
            String cmd = getDriver().getStatusCmd();
            feed = SshFeed.builder()
                    .entity(this)
                    .period(config().get(SERVICE_PROCESS_IS_RUNNING_POLL_PERIOD))
                    .machine(machine.get())
                    .poll(new SshPollConfig<Boolean>(SERVICE_UP)
                            .command(cmd)
                            .setOnSuccess(true)
                            .setOnFailureOrException(false))
                    .build();
        } else {
            LOG.warn("Location(s) {} not an ssh-machine location, so not polling for status; setting serviceUp immediately", getLocations());
            sensors().set(SERVICE_UP, true);
        }
    }

    @Override
    protected void disconnectSensors() {
        if (feed != null) feed.stop();
        super.disconnectSensors();
    }

    @Override
    public void populateServiceNotUpDiagnostics() {
        // TODO no-op currently; should check ssh'able etc
    }

    @Override
    public String ansibleCommand(String module, String args) {
        final ProcessTaskWrapper<Integer> command = getDriver().ansibleCommand(module, args);

        command.asTask().blockUntilEnded();

        if (0 == command.getExitCode()) {
            return command.getStdout();
        } else {
            throw new RuntimeException("Command (" + args + ") in module " + module
                    + " failed with stderr:\n" + command.getStderr() + "\n");
        }
    }
}

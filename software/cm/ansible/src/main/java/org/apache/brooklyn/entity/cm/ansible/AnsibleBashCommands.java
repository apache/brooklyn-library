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
import org.apache.brooklyn.util.core.file.BrooklynOsCommands;
import org.apache.brooklyn.util.ssh.BashCommandsConfigurable;

import static org.apache.brooklyn.util.ssh.BashCommands.sudo;

public class AnsibleBashCommands {

    public static final String INSTALL_ANSIBLE(Entity entity) {
        return INSTALL_ANSIBLE(BrooklynOsCommands.bash(entity, true));
    }

    static final String INSTALL_ANSIBLE(BashCommandsConfigurable cmds) {
        return cmds.chain(
                cmds.ifExecutableElse0("apt-add-repository",sudo("apt-add-repository -y ppa:ansible/ansible")),
                cmds.INSTALL_CURL,
                cmds.INSTALL_TAR,
                cmds.INSTALL_UNZIP,
                cmds.installExecutable("ansible"));
    }

    public static final String INSTALL_ANSIBLE = INSTALL_ANSIBLE(BashCommandsConfigurable.newInstance());

}

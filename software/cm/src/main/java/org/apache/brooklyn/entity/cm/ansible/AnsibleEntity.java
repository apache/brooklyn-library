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

import org.apache.brooklyn.api.entity.ImplementedBy;
import org.apache.brooklyn.core.annotation.Effector;
import org.apache.brooklyn.core.annotation.EffectorParam;
import org.apache.brooklyn.core.effector.MethodEffector;
import org.apache.brooklyn.entity.software.base.SoftwareProcess;

@ImplementedBy(AnsibleEntityImpl.class)
public interface AnsibleEntity extends SoftwareProcess, AnsibleConfig {

    MethodEffector<String> ANSIBLE_COMMAND = new MethodEffector<>(AnsibleEntity.class, "ansibleCommand");

    @Effector(description = "Invoke an arbitrary Ansible command, optionally specifying the module (default is 'command')")
    String ansibleCommand(
        @EffectorParam(name="module", description = "Name of the Ansible module to invoke", defaultValue = "command")
        String module,
        @EffectorParam(name="args", description = "Arguments for the ansible command")
        String args
    );

}

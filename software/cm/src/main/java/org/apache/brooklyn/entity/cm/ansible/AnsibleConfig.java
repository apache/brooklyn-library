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

import org.apache.brooklyn.config.ConfigKey;
import org.apache.brooklyn.core.config.ConfigKeys;
import org.apache.brooklyn.util.core.flags.SetFromFlag;

import com.google.common.annotations.Beta;

/** {@link ConfigKey}s used to configure Ansible */
@Beta
public interface AnsibleConfig {

    public static enum AnsibleModes {
        PLAYBOOK
    };

    @SetFromFlag("playbook")
    public static final ConfigKey<String> ANSIBLE_PLAYBOOK = ConfigKeys.newStringConfigKey("brooklyn.ansible.playbook",
        "Playbook to be execute by Ansible");

    @SetFromFlag("playbook.yaml")
    public static final ConfigKey<String> ANSIBLE_PLAYBOOK_YAML = ConfigKeys.newStringConfigKey("brooklyn.ansible.playbookYaml",
        "Playbook to be execute by Ansible");

    @SetFromFlag("playbook.url")
    public static final ConfigKey<String> ANSIBLE_PLAYBOOK_URL = ConfigKeys.newStringConfigKey("brooklyn.ansible.playbookUrl");

    @SetFromFlag("ansible.service.start")
    public static final ConfigKey<String> ANSIBLE_SERVICE_START = ConfigKeys.newStringConfigKey("ansible.service.start",
            "Default start command used with conjunction with the Ansible's service module",
            "sudo ansible localhost -c local -m service -a \"name=%s state=started\"");

    @SetFromFlag("ansible.service.stop")
    public static final ConfigKey<String> ANSIBLE_SERVICE_STOP = ConfigKeys.newStringConfigKey("ansible.service.stop",
            "Default stop command used with conjunction with the Ansible's service module",
            "sudo ansible localhost -c local -m service -a \"name=%s state=stopped\"");

    @SetFromFlag("ansible.service.checkPort")
    public static final ConfigKey<Integer> ANSIBLE_SERVICE_CHECK_PORT = ConfigKeys.newIntegerConfigKey("ansible.service.check.port");

    @SetFromFlag("service.name")
    public static final ConfigKey<String> SERVICE_NAME = ConfigKeys.newStringConfigKey("brooklyn.ansible.serviceName",
        "Name of OS service this will run as, for use in checking running and stopping");
}

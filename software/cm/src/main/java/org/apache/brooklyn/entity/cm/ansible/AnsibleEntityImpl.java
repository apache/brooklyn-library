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

import org.apache.brooklyn.entity.stock.EffectorStartableImpl;
import org.apache.brooklyn.util.text.Strings;

import static com.google.common.base.Preconditions.checkNotNull;

public class AnsibleEntityImpl extends EffectorStartableImpl implements AnsibleEntity {

    public void init() {
        checkNotNull(getConfig(SERVICE_NAME), "service name is missing. it has to be provided by the user");
        String playbookName = getConfig(ANSIBLE_PLAYBOOK);
        if (!Strings.isBlank(playbookName)) setDefaultDisplayName(playbookName + " (ansible)");

        super.init();
        new AnsibleLifecycleEffectorTasks().attachLifecycleEffectors(this);
    }

    @Override
    public void populateServiceNotUpDiagnostics() {
        // TODO no-op currently; should check ssh'able etc
    }    
}

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
package org.apache.brooklyn.entity.cm.salt.impl;

import org.apache.brooklyn.api.location.MachineLocation;
import org.apache.brooklyn.entity.cm.salt.SaltConfig;
import org.apache.brooklyn.entity.software.base.lifecycle.MachineLifecycleEffectorTasks;

import com.google.common.annotations.Beta;
import com.google.common.base.Supplier;
import org.apache.brooklyn.util.core.config.ConfigBag;

/**
 * Kept only for rebinding to historic persisted state; not used.
 * Not preserving the functionality of any such persisted entities; just ensuring it deserializes.
 */
@Beta
class SaltLifecycleEffectorTasks extends MachineLifecycleEffectorTasks implements SaltConfig {

    @Override
    protected String startProcessesAtMachine(Supplier<MachineLocation> machineS) {
        throw new UnsupportedOperationException("Legacy SaltEntity no longer supported");
    }

    @Override
    protected String stopProcessesAtMachine(ConfigBag params) {
        throw new UnsupportedOperationException("Legacy SaltEntity no longer supported");
    }
    
    @SuppressWarnings("unused")
    private void startWithSshAsync() {
        new Runnable() {
            @Override
            public void run() {
                throw new UnsupportedOperationException("Legacy SaltEntity no longer supported");
            }
        };
        throw new UnsupportedOperationException("Legacy SaltEntity no longer supported");
    }
}

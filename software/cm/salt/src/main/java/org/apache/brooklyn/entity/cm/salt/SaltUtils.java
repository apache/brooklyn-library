/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.brooklyn.entity.cm.salt;

import org.apache.brooklyn.api.location.LocationDefinition;
import org.apache.brooklyn.api.mgmt.ManagementContext;

public class SaltUtils {

    private SaltUtils() {
        // Utility class
    }

    public static ManagementContext.PropertiesReloadListener propertiesReloadListener(
            ManagementContext mc, LocationDefinition definition) {

        return new ManagementContext.PropertiesReloadListener() {
            private static final long serialVersionUID = 1L;
            @Override
            public void reloaded() {
                // TODO: implement properties reload logic
            }
        };
    }

}

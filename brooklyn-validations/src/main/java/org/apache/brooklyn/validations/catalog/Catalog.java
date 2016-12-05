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
package org.apache.brooklyn.validations.catalog;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.Map;

public class Catalog {

    private final Map<String, CatalogProject> catalogProjects;

    public Catalog(Map<String, CatalogProject> catalogProjects) {
        this.catalogProjects = catalogProjects;
    }

    public List<CatalogProject> getCatalogProjects() {
        return ImmutableList.copyOf(catalogProjects.values());
    }

    public Map<String, CatalogProject> getCatalogProjectsMap() {
        return catalogProjects;
    }

    public List<Repository> getRepositories() {
        return Lists.transform(getCatalogProjects(), new Function<CatalogProject, Repository>() {
            public Repository apply(CatalogProject input) {
                return input.getRepository();
            }
        });
    }

    public CatalogProject getCatalogProject(String ownerName, String repoName) {
        String token = ownerName + "/" + repoName;
        return catalogProjects.get(token);
    }
}

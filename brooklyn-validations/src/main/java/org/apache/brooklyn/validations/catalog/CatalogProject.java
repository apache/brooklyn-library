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

import org.apache.brooklyn.util.collections.MutableMap;
import org.apache.brooklyn.util.yaml.Yamls;

import javax.annotation.Nullable;
import java.util.Map;

public class CatalogProject {

    private final Repository repository;

    private final String description;

    private final String catalogBomString;
    private final Map<String, Object> catalogBomYaml;

    private final String documentation;
    private final String masterCommitHash;
    private final String license;
    private final String changelog;

    private final boolean isValid;

    @SuppressWarnings("unchecked")
    public CatalogProject(String repoUrl, String repoName, String author, String description,
                          String catalogBomString, Map<String, Object> catalogBomYaml, @Nullable String documentation,
                          @Nullable String masterCommitHash, @Nullable String license, @Nullable String changelog) {

        this.repository = new Repository(repoUrl, repoName, author);

        this.description = description;

        this.catalogBomString = catalogBomString;
        this.catalogBomYaml = catalogBomYaml;

        this.documentation = documentation;
        this.masterCommitHash = masterCommitHash;
        this.license = license;
        this.changelog = changelog;

        this.isValid = true;
    }

    public CatalogProject(String repoUrl, String repoName, String author) {

        this.repository = new Repository(repoUrl, repoName, author);

        this.description = "";

        this.catalogBomString = "";
        this.catalogBomYaml = MutableMap.of();

        this.documentation = "";
        this.masterCommitHash = "";
        this.license = "";
        this.changelog = "";

        this.isValid = false;
    }

    public Repository getRepository() {
        return repository;
    }

    public String getToken() {
        return getRepository().getToken();
    }

    public String getDescription() {
        return description;
    }

    public String getCatalogBomString() {
        return catalogBomString;
    }

    public Map<String, Object> getCatalogBomYaml() {
        return catalogBomYaml;
    }

    public String getDocumentation() {
        return documentation;
    }

    public String getMasterCommitHash() {
        return masterCommitHash;
    }

    public String getLicense() {
        return license;
    }

    public String getChangelog() {
        return changelog;
    }

    public boolean isValid() {
        return isValid;
    }

}

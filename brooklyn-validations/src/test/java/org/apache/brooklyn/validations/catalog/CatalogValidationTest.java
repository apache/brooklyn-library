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

import com.google.common.base.Optional;
import org.apache.brooklyn.test.Asserts;
import org.apache.brooklyn.util.collections.MutableMap;
import org.testng.annotations.Test;

import java.util.Map;

public class CatalogValidationTest {


    private String REPO_URL = "https://github.com/brooklyncentral/brooklyn-community-catalog";
    private String NOT_VALID_REPO_URL = "https://github.com/iyovcheva/brooklyn-community-catalog";

    private String VALID_CTALOG_URL = "https://github.com/cloudsoft/jmeter-entity";
    private String NOT_VALID_CTALOG_URL = "https://github.com/cloudsoft/bower-cloudsoft-ui-common";

    @Test
    public void testCatalogProjectValidation() {
        String validationResult = validateCatalogRepos(REPO_URL);
        Asserts.assertEquals(validationResult, "", validationResult);

        String validationResultNotValidRepo = validateCatalogRepos(NOT_VALID_REPO_URL);
        Asserts.assertFalse(validationResultNotValidRepo.isEmpty(), "Not valid repo was successfully validated");
    }

    @Test
    public void testSingleCatalogValidation() {
        Asserts.assertTrue(validateCatalogRepo(VALID_CTALOG_URL), "Catalog  " + VALID_CTALOG_URL + " is not valid.");
        Asserts.assertFalse(validateCatalogRepo(NOT_VALID_CTALOG_URL), "Not valid catalog " + NOT_VALID_CTALOG_URL + " is successfully validated.");
    }

    private String validateCatalogRepos(String repo) {
        Catalog catalog = CatalogScraper.scrapeCatalog(repo);
        Map<String, Boolean> validatedCatalogProjects = validateRepos(catalog.getCatalogProjectsMap());
        String result = "";
        for (Map.Entry repoUrl: validatedCatalogProjects.entrySet()) {
            if (repoUrl.getValue().equals(false)) {
                result = result.concat(repoUrl.getKey() + " is not valid catalog\n");
            }
        }
        return result;
    }

    private Map<String, Boolean> validateRepos(Map<?,?> repoUrls) {
        Map<String, Boolean> result = MutableMap.of();
        for (Map.Entry repo: repoUrls.entrySet()) {
            result.put((String) repo.getKey(), ((CatalogProject)repo.getValue()).isValid());
        }
        return result;
    }

    private boolean validateCatalogRepo(String repo) {
        Optional<CatalogProject> catalogProject = CatalogScraper.parseCatalogProject(repo);
        return catalogProject.get().isValid();
    }
}

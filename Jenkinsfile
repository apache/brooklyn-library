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

node(label: 'ubuntu') {
    catchError {
        def environment

        stage('Clone repository') {
            checkout scm
        }

        stage('Prepare environment') {
            echo 'Creating maven cache ...'
            sh 'mkdir -p ${WORKSPACE}/.m2'
            echo 'Building docker image for test environment ...'
            environment = docker.build('brooklyn:${JOB_BASE_NAME}')
        }

        stage('Run tests') {
            environment.inside('-i --name brooklyn-${JOB_BASE_NAME}-${BUILD_ID} -v ${WORKSPACE}/.m2:/root/.m2 -v ${WORKSPACE}:/usr/build -w /usr/build') {
                sh 'mvn clean install'
            }
        }

        stage('Publish results') {
            // Publish JUnit results
            junit allowEmptyResults: true, testResults: '**/target/surefire-reports/junitreports/*.xml'

            // Publish TestNG results
            //step([
            //    $class: 'Publisher',
            //    reportFilenamePattern: '**/testng-results.xml'
            //])
        }

        stage('Deploy artifacts') {
            environment.inside('-i --name brooklyn-${JOB_BASE_NAME}-${BUILD_ID} -v ${WORKSPACE}/.m2:/root/.m2 -v ${WORKSPACE}:/usr/build -w /usr/build') {
                sh 'mvn deploy -DskipTests'
            }
        }
    }

    // ---- Post actions steps ----

    // Send email notifications
    step([
        $class: 'Mailer',
        notifyEveryUnstableBuild: true,
        recipients: 'thomas.bouron@cloudsoftcorp.com',
        sendToIndividuals: false
    ])

    // TODO: Add build other project
}
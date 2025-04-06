pipeline {
    agent any
    tools {
        maven 'maven'
        jdk 'jdk17'
    }
    stages {
        stage ('Initialize') {
            steps {
                sh '''
                    echo "PATH = ${PATH}"
                    echo "M2_HOME = ${M2_HOME}"
                    mysql -e 'drop database IF EXISTS justjournal_test;'
                    mysql -e 'create database IF NOT EXISTS justjournal_test;'
                    mysql --database=justjournal_test < src/main/resources/db/migration/V1_0__jj_create.sql
                    mysql --database=justjournal_test < database/jj_data_load.sql
                    mysql -e 'GRANT all ON justjournal_test.* TO travis@localhost';
                '''
            }
        }
        stage('Build') {
            steps {
                sh 'mvn -B -DskipTests clean package'
            }
        }
        stage('Test') {
            steps {
                sh 'mvn test'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }
        stage('Integration Test') {
            steps {
                sh 'mvn integration-test'
            }
            post {
                always {
                   junit 'target/failsafe-reports/*.xml'
            	}
            }
       }
       stage('Coverage') {
            steps {
                sh 'mvn jacoco:report'
                recordCoverage(tools: [[parser: 'JACOCO']], id: 'jacoco', name: 'JaCoCo Coverage', sourceCodeRetention: 'EVERY_BUILD', enabledForFailure: true,
        qualityGates: [
                [threshold: 60.0, metric: 'LINE', baseline: 'PROJECT', unstable: true],
                [threshold: 60.0, metric: 'BRANCH', baseline: 'PROJECT', unstable: true]])
            }
        }
       stage('Sonarqube') {
            steps {
                withSonarQubeEnv('sonarcloud') {
                    sh 'mvn sonar:sonar -Dsonar.organization=laffer1-github -Dsonar.projectKey=laffer1_justjournal -Dsonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml'
                }
                timeout(time: 10, unit: 'MINUTES') {
                    waitForQualityGate abortPipeline: true
                }
            }
       }
    }
}

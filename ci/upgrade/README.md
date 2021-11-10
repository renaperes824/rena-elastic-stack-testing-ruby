# ESS Automated Upgrades

## Workflow

To contribute to ESS automated upgrades, there are two steps:

1. Create API setup: The API setup is done in [this repo](https://github.com/elastic/elastic-stack-testing.git), follow example:
`buildSrc/src/main/java/org/estf/gradle/UploadData.java`

2. Create UI or API verification: The verification can be in this repo or another. Currently the main tests reside in [kibana repo](https://github.com/elastic/kibana.git) under `x-pack/test/upgrade`

If you have another workflow or are unsure how your test plan can be automated, please create an [issue](https://github.com/elastic/elastic-stack-testing/issues/new) to evaluate your requirements.

## Development Environment

1. Install Java 12 and set JAVA_HOME to point to this version
2. Create a [github API token](https://blog.github.com/2013-05-16-personal-api-tokens/) to access the Cloud SDK.  The API key will need repo access (repo checkbox).  Once a github API token has been created set the following environment variables: `GH_OWNER` and `GH_TOKEN`.  `GH_OWNER` should be set to `elastic`.
3. Download libs, run `./downloadLibs.sh`

## Create API Setup

1. In `buildSrc/src/main/java/org/estf/gradle` create a java class for your API setup, you can follow `UploadData.java` as an example
2. Add a task to test your API in `tests/build.gradle`
3. Manually create an ESS deployment and fill out the following in `tests/build.gradle`:
```
task deployment {
    doFirst {
        ext.esBaseUrl = ""
        ext.kbnBaseUrl = ""
        ext.username = ""
        ext.password = ""
        ext.version = ""
        ext.upgradeVersion = ""
    }
}
```
4. Run your task `./gradlew <your task name>`, for example: `./gradlew testSampleData`

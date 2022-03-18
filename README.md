# elastic-stack-testing

Elastic Stack Testing Framework (ESTF)

This project provides a common automation framework for elastic stack testing.
<br>The goal is to provide a powerful, easy to use and maintain framework to build test suites.

More details can be found:
- [Wiki](https://github.com/elastic/elastic-stack-testing/wiki)
- [Kanban Board](https://github.com/elastic/elastic-stack-testing/projects)

## Infrastructure

- Software products under test: Elasticsearch, Kibana, Logstash, Beats, Cloud, APM, ML
- Ansible is used to install and configure the software products under test
- Python, Pytest and Selenium/Webium will be used for the test framework
- Automated vagrant provider support for virtualbox and docker
- Cloud testing uses Gradle/Java

## Environment Setup

* Install Latest Python 3
  * https://www.python.org/downloads/

* Install Latest Vagrant
  * https://www.vagrantup.com/downloads.html

* Install Vagrant Provider

  Virtualbox or Docker
  For M1 hardware Virtualbox is not supported.

  * Install Latest Virtualbox
    * https://www.virtualbox.org/wiki/Downloads
  * Install Latest Docker
    * https://docs.docker.com/get-docker/

## Quick Start
Running a playbook for provisioning

1. Clone repository: `git clone https://github.com/elastic/elastic-stack-testing.git`
2. `cd elastic-stack-testing`
3. Select a build URL and switch to the appropriate branch, for example:
  ```
  git checkout 7.17
  export ES_BUILD_URL=artifacts.elastic.co/7.17.0

  **Note: Docker provider, the following is required:
  export VAGRANT_DEFAULT_PROVIDER=docker

  **Note: On M1 hardware, seting docker provider and following is required:
  export ES_BUILD_ARCH=arm64
  ```
4. Run the build: `./buildenv.sh`

For more options see file: `buildenv.sh`

## Accessing Stack

After successful installation, endpoints can be access as follows:

```
Elasticsearch: https://localhost:9200
Kibana: https://localhost:5601
User: elastic
Password: changeme
```

## Currently Supported

  - Machine: `Vagrant, Virtualbox or Docker`
  - Machine OS: `Ubuntu 18 or Ubuntu 20`
  - Architecture: `x86_64 or arm64`
  - Node: `Single`
  - Product Versions: `5.6.x, 6.x, 7.x, 8.x`
  - Product Packages: `tar.gz`

## Cloud Environment

  Building the `ci/cloud` project requires a [github API token](https://blog.github.com/2013-05-16-personal-api-tokens/).
  The API key will need repo access (repo checkbox).

  Once a github API token has been acquired three environment variables must be set: `GH_OWNER`, `GH_TOKEN`, and `SDK_VERSION`.

  `GH_OWNER` should be set to `elastic` but can be overridden to your fork if necessary.

  `chmod +x downloadLibs.sh`

  `GH_OWNER=elastic GH_TOKEN=mytoken SDK_VERSION=1.2.0-SNAPSHOT ./downloadLibs.sh`

## Contributing

  Please use the [issue tracker](https://github.com/elastic/elastic-stack-testing/issues) to report any bugs or enhancement requests.  Pull requests are welcome.

## Authors

  Elastic Stack Testing Framework created by [Liza Dayoub](https://github.com/liza-mae).

  Also see a list of [contributors](https://github.com/elastic/elastic-stack-testing/graphs/contributors) who participated in the project.

## License

  Apache License 2.0

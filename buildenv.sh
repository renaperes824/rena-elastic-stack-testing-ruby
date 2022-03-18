#!/bin/bash
#
# @author: Liza Dayoub

# When set, we are running locally not in Jenkins
export AIT_RUN_LOCAL=true

# The default vagrant provider is virtualbox.
# To change the provider to docker, please set
# export VAGRANT_DEFAULT_PROVIDER=docker

# Build URL
export ES_BUILD_URL=${ES_BUILD_URL:-}

# Build Type
export ES_BUILD_OSS=false

# Build package extension
export ES_BUILD_PKG_EXT=tar

# The default machine architecture is x86_64
# To change to arm, please set
# export ES_BUILD_ARCH=arm64

# Install package
export AIT_ANSIBLE_PLAYBOOK=get_started/install_xpack

# Setup VM
export AIT_VM=vagrant_vm

# Skip destroying the VM
export AIT_SKIP_VM_CLEANUP=true

source jenkins_build.sh

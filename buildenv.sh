#!/bin/bash
#
# @author: Liza Dayoub

#-- Run locally
export AIT_RUN_LOCAL=true

#-- Build URL
#   Accepted formats: artifacts.elastic.co/8.0.1, snapshots.elastic.co/8.2.0-234fa56
#   staging.elastic.co/8.1.1-2346753a
export ES_BUILD_URL=${ES_BUILD_URL:-}

#-- Playbook
#   Playbooks are in the playbooks directory, full path does not need to specified
export AIT_ANSIBLE_PLAYBOOK=${AIT_ANSIBLE_PLAYBOOK:-get_started/install_elk}

#-- Vagrant Provider
#   The default vagrant provider is virtualbox.
#   Virtualbox is not supported on arm
#   If running on arm, also set ES_BUILD_ARCH
# export VAGRANT_DEFAULT_PROVIDER=docker

#-- Machine Architecture
#   The default machine architecture is x86_64
# export ES_BUILD_ARCH=arm64

#-- Build Type
#   Build oss version, false is only option for recent builds
export ES_BUILD_OSS=false

#-- Build package extension
#   Build extension, only tar is currently available
export ES_BUILD_PKG_EXT=tar

#-- Setup new VM
#   When AIT_HOST_INVENTORY_DIR is set, this will be skipped
export AIT_VM=vagrant_vm

#-- Reuse an existing VM
#   This will resuse the same VM to rerun playbook.  Find the instance you
#   want to use by doing an ls in the ait_workspace directory.  The name of
#   the directory will be will be in format: 8-0-1_os which is set below
export AIT_HOST_INVENTORY_DIR=${AIT_HOST_INVENTORY_DIR:-}

#-- Skip destroying the VM
#   When set to false, it will shutdown the VM, if set to true it remains running
export AIT_SKIP_VM_CLEANUP=true

source jenkins_build.sh

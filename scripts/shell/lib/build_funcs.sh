#!/bin/bash
#
# Build shell functions
#
# @author: Liza Dayoub

source ${AIT_SCRIPTS}/shell/lib/logging_funcs.sh

# ----------------------------------------------------------------------------
running_in_jenkins() {
  if  [ -z $AIT_RUN_LOCAL ] || [ `whoami` == "jenkins" ]; then
    return 1
  fi
  return 0
}

# ----------------------------------------------------------------------------
export_env_vars() {
  # Export environment variables
  if [ ! -z "${AIT_ENV_VARS}" ]; then
    echo_info "Export environment variables"
    AIT_ENV_VARS="${AIT_ENV_VARS//$'\n'/ }"
    eval export "${AIT_ENV_VARS}"
  fi
  # If running in Jenkins, set to run headless browser
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ]; then
    export AIT_RUN_HEADLESS_BROWSER=true
  fi

  # Added to get latest build from server
  if [ ! -z $ES_BUILD_SERVER ] && [ ! -z $ES_BUILD_BRANCH ]; then

    # Translate branch to a version, sometimes this comes back null so retry
    retry=0
    maxRetries=10
    retryInterval=5
    until [ ${retry} -ge ${maxRetries} ]
    do
      ES_BUILD_VERSION=$(curl -s "https://artifacts-api.elastic.co/v1/branches/${ES_BUILD_BRANCH##*/}" | jq -r .branch.builds[0].version)
      echo_debug "ES_BUILD_VERSION: $ES_BUILD_VERSION"
      if [ ! -z $ES_BUILD_VERSION ] && [[ $ES_BUILD_VERSION != "null" ]]; then
        break;
      fi
      retry=$[${retry}+1]
      echo_debug "Retrying [${retry}/${maxRetries}] in ${retryInterval}(s)"
      sleep ${retryInterval}
    done

    if [ ${retry} -ge ${maxRetries} ]; then
      echo_error "Failed to get ES_BUILD_VERSION after ${maxRetries} attempts!"
    fi

    # Find latest build based on the version
    ARTIFACTS_BASE_URL="https://artifacts-api.elastic.co/v1/versions/${ES_BUILD_VERSION}/builds/latest"
    if [[ "$ES_BUILD_SERVER" =~ "snapshots" ]] && [[ "$ES_BUILD_VERSION" != *"SNAPSHOT"* ]]; then
      ARTIFACTS_BASE_URL="https://artifacts-api.elastic.co/v1/versions/${ES_BUILD_VERSION}-SNAPSHOT/builds/latest"
    elif [[ "$ES_BUILD_SERVER" =~ "staging" ]] && [[ "$ES_BUILD_VERSION" =~ "SNAPSHOT" ]]; then
      ARTIFACTS_BASE_URL="https://artifacts-api.elastic.co/v1/versions/${ES_BUILD_VERSION//[!0-9.]/}/builds/latest"
    fi

    echo_debug "ES_BUILD_VERSION: $ES_BUILD_VERSION"
    echo_debug "ARTIFACTS_BASE_URL: $ARTIFACTS_BASE_URL"

    # Get latest build id, sometimes this comes back null so retry
    retry=0
    maxRetries=10
    retryInterval=5
    until [ ${retry} -ge ${maxRetries} ]
    do
      LATEST_BUILD_ID=$(curl -s ${ARTIFACTS_BASE_URL} | jq -r .build.build_id)
      echo_debug "LATEST_BUILD_ID: $LATEST_BUILD_ID"
      if  [ ! -z $LATEST_BUILD_ID ] && [[ $LATEST_BUILD_ID != "null" ]]; then
        break;
      fi
      retry=$[${retry}+1]
      echo_debug "Retrying [${retry}/${maxRetries}] in ${retryInterval}(s)"
      sleep ${retryInterval}
    done

    if [ ${retry} -ge ${maxRetries} ]; then
      echo_error "Failed to get LATEST_BUILD_ID after ${maxRetries} attempts!"
    fi

    export LATEST_BUILD_ID
    export ES_BUILD_URL="${ES_BUILD_SERVER}.elastic.co/$LATEST_BUILD_ID"

    echo_debug "ES_BUILD_URL: $ES_BUILD_URL"
  fi

  env | sort
}

# ----------------------------------------------------------------------------
create_workspace() {
  # If running in Jenkins, return
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ]; then
    return
  fi
  # If not running in Jenkins, create a local workspace
  if [ -z $WORKSPACE ]; then
    if [ ! -d ait_workspace ]; then
      mkdir ait_workspace
    fi
    export WORKSPACE=${AIT_ROOTDIR}/ait_workspace
  fi
}

# ----------------------------------------------------------------------------
check_env_workspace() {
  # If variable is empty, throw error and exit
  create_workspace
  if [ -z $WORKSPACE ]; then
    echo_error "Environment variable: WORKSPACE is not defined"
    exit 1
  fi
  export AIT_ANSIBLE_ES_VARS=${WORKSPACE}/vars.yml
}

# ----------------------------------------------------------------------------
check_env_vm() {
  # If variable is empty or points to a valid file, return
  if [ -z $AIT_VM ] || [ -f $AIT_VM ]; then
    return
  fi
  # Try to form a valid file, if not throw error and exit
  chk1=${AIT_SCRIPTS}/shell/${AIT_VM}
  chk2=${AIT_SCRIPTS}/shell/${AIT_VM}.sh
  if [ ! -z $chk1 ] && [ -f $chk1 ]; then
    export AIT_VM=$chk1
    return
  fi
  if [ ! -z $chk2 ] && [ -f $chk2 ]; then
    export AIT_VM=$chk2
    return
  fi
  echo_error "Invalid VM setup script: $AIT_VM, check directory ${AIT_SCRIPTS}/shell"
  exit 1
}

# ----------------------------------------------------------------------------
check_env_ansible_playbook() {
  # If running in Jenkins or variable is empty or points to a valid file, return
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ] ||
     [ -z $AIT_ANSIBLE_PLAYBOOK ] || [ -f $AIT_ANSIBLE_PLAYBOOK ]; then
    return
  fi
  # Try to form a valid playbook, if not throw error and exit
  chk1=${AIT_ANSIBLE_PLAYBOOK_DIR}/${AIT_ANSIBLE_PLAYBOOK}
  chk2=${AIT_ANSIBLE_PLAYBOOK_DIR}/${AIT_ANSIBLE_PLAYBOOK}.yml
  if [ ! -z $chk1 ] && [ -f $chk1 ]; then
    export AIT_ANSIBLE_PLAYBOOK=$chk1
    return
  fi
  if [ ! -z $chk2 ] && [ -f $chk2 ]; then
    export AIT_ANSIBLE_PLAYBOOK=$chk2
    return
  fi
  echo_error "Invalid ansible playbook!"
  exit 1
}

# ----------------------------------------------------------------------------
check_env_tests() {
  # If variable is empty or points to a valid file, return
  if [ -z $AIT_TESTS ] || [ -f $AIT_TESTS ]; then
    return
  fi
  # Try to form a valid playbook, if not throw error and exit
  chk1=${AIT_SCRIPTS}/shell/${AIT_TESTS}
  chk2=${AIT_SCRIPTS}/shell/${AIT_TESTS}.sh
  chk3=${AIT_ROOTDIR}/tests/integration/tests/${AIT_TESTS}.py
  if [ ! -z $chk1 ] && [ -f $chk1 ]; then
    export AIT_TESTS=$chk1
    return
  fi
  if [ ! -z $chk2 ] && [ -f $chk2 ]; then
    export AIT_TESTS=$chk2
    return
  fi
  if [ ! -z $chk3 ] && [ -f $chk3 ]; then
    export AIT_TESTS=$chk3
    return
  fi
  echo_error "Invalid test script!"
  exit 1
}

# ----------------------------------------------------------------------------
check_python_virtual_env() {
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ] && [ -z $PYENV_VIRTUALENV_INIT ] ||
     [ $RC == 0 ] && [ -z $VIRTUAL_ENV ]; then
    echo "Python virtual envrionment is not activated"
    exit 1
  fi
  echo_info "Upgrade pip"
  $PYTHON_EXE -m pip install --upgrade pip
}

# ----------------------------------------------------------------------------
function compare_ver {
  printf "%03d%03d%03d%03d" $(echo "$1" | tr '.' ' ');
}

# ----------------------------------------------------------------------------
function set_python_exe {
  if [ ! -z $PYTHON_EXE ]; then
    return
  fi
  least_ver="3.6"
  for p in $(compgen -c python | grep -v "config"); do
    curr_ver=$(echo $p | egrep -o '[0-9]+\.[0-9]+')
    if [ $(compare_ver $curr_ver) -ge $(compare_ver $least_ver) ]; then
      export PYTHON_EXE="python$curr_ver"
      break
    fi
  done
  if [ -z $PYTHON_EXE ]; then
    echo_error "Need at least python ${least_ver} installed, none found!"
  fi
}

# ----------------------------------------------------------------------------
activate_python_virtual_env() {
  # If running in Jenkins, return
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ]; then
    return
  fi
  echo_info "Create and activate python venv"
  export PYTHON_VENV_NAME=${WORKSPACE}/es-venv
  # Create python virtual env
  set_python_exe
  $PYTHON_EXE -m venv ${PYTHON_VENV_NAME}
  # Activate env
  source ${PYTHON_VENV_NAME}/bin/activate
  echo_info "Python venv name: $PYTHON_VENV_NAME"
}

# ----------------------------------------------------------------------------
python_install_packages() {
  type=$1; # cloud or empty
  check_python_virtual_env
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ] && [ ! -z $PYENV_VIRTUALENV_INIT ]; then
    echo_info "Install python"
    pyver=$(cat .python-version)
    pyenv install -s $pyver
    pyenv global $pyver
    echo_info "Set python exe"
    export PYTHON_EXE="python$(cat .python-version | egrep -o '[0-9]+\.[0-9]+')"
  fi
  echo_info "Install python packages"
  if [ ! -z $type ] && [ "$type" == "cloud" ]; then
    echo_info "requirements_cloud.txt"
    $PYTHON_EXE -m pip install -r requirements_cloud.txt
  else
    echo_info "requirements.txt"
    $PYTHON_EXE -m pip install -r requirements.txt
   fi
  echo_info "List installed python packages"
  $PYTHON_EXE -m pip list
}

# ----------------------------------------------------------------------------
java_install_packages() {
  check_python_virtual_env
  echo_info "Install java sdk package"
  if [ ! -z $ESTF_UPGRADE_CLOUD_VERSION ] || ([ ! -z $TASK ] && [ $TASK == "kibana_upgrade_tests" ]); then
     $PYTHON_EXE ${AIT_SCRIPTS}/python/install_cloud_sdk_upgrades.py
  else
    $PYTHON_EXE ${AIT_SCRIPTS}/python/install_cloud_sdk.py
  fi
  RC=$?
  if [ $RC -ne 0 ]; then
    echo_error "FAILED! Java SDK not installed"
    exit 1
  fi
}

# ----------------------------------------------------------------------------
generate_build_variables() {
  # If skip variable set, return
  if [ ! -z $AIT_SKIP_GEN_BUILD_VARS ]; then
    return
  fi
  # If variables are empty, throw error and exit
  if [ -z $ES_BUILD_URL ] || [ -z $ES_BUILD_PKG_EXT ]; then
    echo_error "ES_BUILD_URL and ES_BUILD_PKG_EXT can't be empty"
    exit 1
  fi
  # Create build vars file for ansible
  echo_info "Run script to build ansible variables from env"
  $PYTHON_EXE ${AIT_SCRIPTS}/python/ansible_es_build_vars.py
  if [ $? -ne 0 ]; then
    echo_error "FAILED! Did not create ansible build variables!"
    exit 1
  fi
  if [ ! -z $AIT_ANSIBLE_ES_VARS ] && [ ! -f $AIT_ANSIBLE_ES_VARS ]; then
    echo_error "Build variables file does not exist!"
    exit 1
  fi
  file_lines=`wc -l ${AIT_ANSIBLE_ES_VARS} | awk '{print $1}'`
  if [ $file_lines -le 1 ]; then
    echo_error "Build variables not all generated!"
    exit 1
  fi
}

# ----------------------------------------------------------------------------
run_vm() {
  action=$1; # provision
  if [ -z $action ]; then
    action="up"
  fi
  on_fail=$2; # exit or continue
  if [ -z $on_fail ]; then
    on_fail="exit"
  fi
  check_env_vm
  check_env_ansible_playbook
  # If variable is empty, return
  if [ -z $AIT_VM ]; then
    return
  fi
  # If not a valid file, throw an error and exit
  if [ ! -f $AIT_VM ]; then
    echo_error "Invalid file: $AIT_VM"
    exit 1
  fi

  # Load docker image, if provider is selected
  if [ ! -z $VAGRANT_DEFAULT_PROVIDER ] && [ "$VAGRANT_DEFAULT_PROVIDER" == "docker" ]; then
    if [ -z $(docker images -q estf-vagrant-docker:1.0) ] ||
      ([ ! -z $VAGRANT_FORCE_DOCKER_DOWNLOAD ] && [ $VAGRANT_FORCE_DOCKER_DOWNLOAD == "true" ]); then
      echo "Download vagrant docker image..."
      base_url="https://github.com/elastic/elastic-stack-testing/releases/download"
      tag="estf-vagrant-docker-v1.0"
      image_name="estf-vagrant-docker.tar.gz"
      if [ ! -z $ES_BUILD_ARCH ] && [ "$ES_BUILD_ARCH" == "arm64" ]; then
        tag="estf-vagrant-docker-arm64-v1.0"
        image_name="estf-vagrant-docker-arm64.tar.gz"
      fi
      docker_package="${base_url}/${tag}/${image_name}"
      echo_info "Get docker image: ${docker_package}"
      curl -sL ${docker_package} -o $image_name
      if [ $? -ne 0 ]; then
        echo_error "Unable to get url: ${docker_package}"
      fi
      docker load < $image_name
      if [ $? -ne 0 ]; then
        echo_error "Failed to docker load: ${image_name}"
      fi
      rm $image_name
    fi
  fi

  # Sync folder
  # NOTE: Right now this is only for Kibana directory
  # if variable is empty, return
  if [ ! -z $AIT_SYNC_KBN_DIR ] && [ "$AIT_SYNC_KBN_DIR" == "yes" ]; then
    _ext=$(grep package_ext $WORKSPACE/vars.yml | sed "s/.*: //")
    _kibana_dir=$(grep kibana_package_url $WORKSPACE/vars.yml | sed "s/.*\///" | sed s/"\.$_ext"//g)
    export AIT_SYNC_FOLDER_NAME=$AIT_VM_INSTALL_DIR/$_kibana_dir
  fi

  # Run vm shell script
  echo_info "Run script: ${AIT_VM}"
  cd $(dirname $AIT_VM)
  $AIT_VM $action
  RC=$?
  # If vm fails, throw error and exit
  if [ $RC -ne 0 ]; then
    echo_error "VM failed!"
    if [ $on_fail == "exit" ] && ( [ $action == "up" ] || [ $action == "provision" ] ); then
      $AIT_VM save_snapshot_cleanup
      exit 1
    fi
  fi
}

# ----------------------------------------------------------------------------
run_ansible_playbook() {
  on_fail=$1; # exit or continue
  if [ -z $on_fail ]; then
    on_fail="exit"
  fi
  check_env_ansible_playbook
  # If running in Jenkins or AIT_VM is not empty or AIT_ANSIBLE_PLAYBOOK is empty, return
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ] ||
     [ ! -z $AIT_VM ] || [ -z $AIT_ANSIBLE_PLAYBOOK ]; then
    return
  fi
  # If AIT_ANSIBLE_PLAYBOOK is not a file, throw error and exit
  if [ ! -f $AIT_ANSIBLE_PLAYBOOK ]; then
    echo_error "Invalid file: $AIT_ANSIBLE_PLAYBOOK"
    exit 1
  fi
  inventory_file=${WORKSPACE}/${AIT_HOST_INVENTORY_DIR}/.vagrant/provisioners/ansible/inventory/vagrant_ansible_inventory
  if [ ! -f ${inventory_file} ]; then
    echo_error "Invalid file: ${inventory_file}"
    exit 1
  fi
  echo_info "Run playbook: ANSIBLE_GROUP_VARS=${WORKSPACE}/vars.yml AIT_UUT=aithost ansible-playbook -i ${inventory_file} ${AIT_ANSIBLE_PLAYBOOK}"
  cd $(dirname $AIT_ANSIBLE_PLAYBOOK)
  ANSIBLE_GROUP_VARS=${WORKSPACE}/vars.yml AIT_UUT=aithost ansible-playbook -i ${inventory_file} $AIT_ANSIBLE_PLAYBOOK
  RC=$?
  if [ $RC -ne 0 ]; then
    echo_error "Playbook failed!"
    if [ $on_fail == "exit" ]; then
      exit 1
    fi
  fi
}

# ----------------------------------------------------------------------------
run_tests() {
  on_fail=$1; # exit or continue
  if [ -z $on_fail ]; then
    on_fail="exit"
  fi
  check_env_vm
  check_env_tests
  # If variable is empty, then return
  if [ -z $AIT_TESTS ]; then
    return
  fi
  # If not a valid file, then throw error
  if [ ! -f $AIT_TESTS ]; then
    echo_error "Invalid file: $AIT_TESTS"
    exit 1
  fi
  # Run tests
  echo_info "Run script: ${AIT_TESTS}"
  cd $(dirname $AIT_TESTS)
  $AIT_TESTS
  RC=$?
  if [ $RC -ne 0 ]; then
    echo_error "Tests failed!"
    if [ $on_fail == "exit" ]; then
      if [ ! -z $AIT_VM ]; then
        $AIT_VM save_snapshot_cleanup
      fi
      exit 1
    fi
  fi
}

# ----------------------------------------------------------------------------
run_cloud_tests() {
  if [ ! -z $ESTF_UPGRADE_CLOUD_VERSION ]; then
    export TASK=kibana_upgrade_tests
  fi
  if [ -z $TASK ]; then
    echo_error "Gradle task name must be supplied"
    exit 1
  fi
  cd ${AIT_CI_CLOUD_DIR}
  if [ $TASK == "kibana_upgrade_tests" ]; then
    # If running in Jenkins, set java version
    running_in_jenkins
    RC=$?
    if [ $RC == 1 ]; then
      export JAVA_HOME="/var/lib/jenkins/.java/openjdk12"
    fi
    cd ${AIT_CI_CLOUD_UPGRADE_DIR}
  fi
  ./gradlew $TASK
  RC=$?
  if [ $RC -ne 0 ]; then
    echo_error "Tests failed!"
    exit 1
  fi
}

deactivate_python_virtual_env() {
  # If running in Jenkins, return
  running_in_jenkins
  RC=$?
  if [ $RC == 1 ]; then
    return
  fi
  echo_info "Deactivate python venv"
  deactivate
}

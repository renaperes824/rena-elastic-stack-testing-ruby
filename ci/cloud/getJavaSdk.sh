#!/bin/bash
export PYTHONIOENCODING=utf8

libsDir="buildSrc/libs"
ghOwner="${GH_OWNER:?GH_OWNER needs to be set!}"
ghToken="${GH_TOKEN:?GH_TOKEN needs to be set!}"
sdkVersion="${SDK_VERSION:?SDK_VERSION needs to be set!}"

getReleaseByTagUrl="https://api.github.com/repos/${ghOwner}/cloud-sdk-java/releases/tags/v${sdkVersion}?access_token=${ghToken}"

if [ ! -d ${libsDir} ]; then
	echo "Creating libs directory..."
	mkdir ${libsDir}
	echo "Created!"
fi

getReleaseByTagResponse=$(curl -s ${getReleaseByTagUrl})
assetName=$(curl -s ${getReleaseByTagUrl} | python -c "import sys, json;print json.load(sys.stdin)['assets'][0]['name']")
assetUrl=$(curl -s ${getReleaseByTagUrl} | python -c "import sys, json; print json.load(sys.stdin)['assets'][0]['url']")
assetUrlWithAuth="${assetUrl}?access_token=${ghToken}"
downloadResponse=$(curl -L -H "Accept: application/octet-stream" ${assetUrlWithAuth} -o ${libsDir}/${assetName})
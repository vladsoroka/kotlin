#!/bin/sh

targetRepo="$2"

remoteRefs=""

while read localRef localSha remoteRef remoteSha
do
	if [ -z "$VAR" ]; then
		remoteRefs="$remoteRef"
	else
		remoteRefs="$remoteRefs,$remoteRef"
	fi

done

mkdir -p ./build/prePushHook
javac -d ./build/prePushHook ./libraries/tools/kotlin-prepush-hook/src/KotlinPrePushHook.java
cd ./build/prePushHook

java KotlinPrePushHook $remoteRefs $targetRepo
returnCode=$?

cd ../..

exit $returnCode
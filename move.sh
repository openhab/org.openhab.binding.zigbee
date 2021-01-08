#/bin/bash

# Script for openhab-core
# Move all from smarthome to openhab

# After running this script it will break on the compat1x package as that will have conflicts.

OLD_LOC="/org/openhab/core/"
NEW_LOC="/org/openhab/core/"

# Move all source files to new openhab location
function moveAll {
    find $1 -type f | grep -v .git | grep -v "/target/" | while read f
    do
        newfile="${f/$OLD_LOC/$NEW_LOC}"
        if [[ $f != $newfile ]]; then
            olddir=`dirname $f`
            newloc=`dirname $newfile`
            newfile="${newfile/core\/core}"
            newloc="${newloc/core\/core}"
            echo move $f "==>" $newfile
            mkdir -p $newloc
            mv $f $newfile
            # bruteforce remove empty smarthome directories
            rmdir -p $olddir 2> /dev/null
        fi
    done
}

# Replace eclipse/smarthome with openhab in files.
function replaceAll {
    find $1 -type f | grep -v .git | grep -v "/target/" | while read f
    do
        echo replace in file $f
        gsed -i "s|org\(.\)eclipse.smarthome|org\1openhab\1core|g" "$f"
        gsed -i "s|https://openhab.org|https://openhab.org|g" "$f"
        gsed -i "s|https://openhab.org|https://openhab.org|g" "$f"
        gsed -i "s|//openhab.org|//openhab.org|g" "$f"
        # bruteforce strip double core names 
        gsed -i "s|core\(.\)core|g" "$f"
    done  
}

# Specific openhab-core patch
function corePatches {
    sed -i "s|\"org\", \"eclipse\", \"smarthome\"|\"org\", \"openhab\"|g" "$1/bundles/org.openhab.core.model.script/src/org/openhab/core/model/script/scoping/ScriptImportSectionNamespaceScopeProvider.java"
    sed -i "s|\(\"model\", \"script\", \"actions\"\)|\"core\", \1|g" "$1/bundles/org.openhab.core.model.script/src/org/openhab/core/model/script/scoping/ScriptImportSectionNamespaceScopeProvider.java"
}

#moveAll $1
replaceAll $1

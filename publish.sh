#!/bin/bash
/opt/play-1.2.6/play dependencies --sync --verbose
ant -Dplay.path=/opt/play-1.2.6
mkdir -p package
zip package/cucumber-0.3.zip -x package -r *

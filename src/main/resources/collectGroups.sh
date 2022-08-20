#!/bin/bash
#run script with login shell
set -v
pwd
gam print groups > remoteGroups
gam print group-members > groups.csv
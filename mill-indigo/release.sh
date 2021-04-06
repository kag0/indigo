#!/usr/bin/env bash

set -e

export GPG_TTY=$(tty)

source credentials.sh

mill clean mill-indigo[2.13]
# mill clean mill-indigo[3.0.0-RC2]

mill mill-indigo[2.13].compile
# mill mill-indigo[3.0.0-RC2].compile

mill mill-indigo[2.13].test
# mill mill-indigo[3.0.0-RC2].test

mill -i mill-indigo[2.13].publish --sonatypeCreds $SONATYPE_USERNAME:$SONATYPE_PASSWORD --release true
# mill -i mill-indigo[3.0.0-RC2].publish --sonatypeCreds $SONATYPE_USERNAME:$SONATYPE_PASSWORD --release true

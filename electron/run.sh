#!/bin/bash
# Ensures the app is built before running, or assumes it is built.
# To be safe, we can just run the electron executable against the current directory,
# which uses package.json 'main' entry point (dist-electron/main/main.js).

npx electron .


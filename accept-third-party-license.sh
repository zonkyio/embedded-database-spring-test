#!/bin/sh

{
  echo "mcr.microsoft.com/mssql/server:2017-latest"
  echo "mcr.microsoft.com/mssql/server:2017-CU20"
} > embedded-database-spring-test/src/test/resources/container-license-acceptance.txt
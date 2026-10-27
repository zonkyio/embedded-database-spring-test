#!/bin/sh

{
  echo "mcr.microsoft.com/mssql/server:2025-latest"
  echo "mcr.microsoft.com/mssql/server:2022-latest"
  echo "mcr.microsoft.com/mssql/server:2019-latest"
  echo "mcr.microsoft.com/mssql/server:2017-latest"
  echo "mcr.microsoft.com/mssql/server:2017-CU30-ubuntu-18.04"
} > embedded-database-spring-test/src/test/resources/container-license-acceptance.txt
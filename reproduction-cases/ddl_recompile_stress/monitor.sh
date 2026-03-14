#!/bin/bash

DB_CONN="TEST_USER/TEST_PASS"

echo "Checking invalid objects"

tbsql $DB_CONN @sql/08_check_invalid_objects.sql
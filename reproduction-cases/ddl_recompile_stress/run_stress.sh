#!/bin/bash

DB_CONN="TEST_USER/TEST_PASS"

echo "Start stress sessions"

for i in 1 2 3
do
tbsql $DB_CONN @sql/03_complex_query_stress.sql &
done

for i in 1 2 3
do
tbsql $DB_CONN @sql/06_call_stress_proc.sql &
done

echo "Stress running"
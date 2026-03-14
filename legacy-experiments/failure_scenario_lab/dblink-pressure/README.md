# DB Link Failure Pressure Test

## Overview

This scenario reproduces instability during
distributed transactions using database links.

## Failure model

Test introduces:

- Concurrent DB link DML
- Session termination
- Shared pool flush

## Architecture

Cluster A ↔ Cluster B

XA transactions executed concurrently.

## Execution

THREAD_COUNT=20 java DistributedTransactionFailureTest

## Expected behavior

Database should:

- Maintain consistency
- Handle rollback correctly
- Avoid memory corruption

## Engineering notes

Inspired by real production incident analysis.
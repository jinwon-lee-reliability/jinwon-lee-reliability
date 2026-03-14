# Database OOM & XA Failure Pressure Test

## Overview

This scenario reproduces database instability under combined memory pressure and distributed transaction load.

The purpose of this test is to observe database behavior when:

- Library cache pressure increases
- Dictionary cache churn occurs
- XA transactions are interrupted
- Sessions are forcefully terminated
- Shared pool is flushed during workload

This scenario simulates real production failure patterns observed in distributed database environments.

---

## Failure Model

This test introduces multiple concurrent failure factors:

### Memory pressure workload

Simulates OOM-like conditions by generating:

- Continuous DDL operations
- High SQL parse activity
- PL/SQL compilation pressure
- Sequence create/drop churn
- Cursor allocation pressure

Target areas:

- Library Cache
- Dictionary Cache
- Cursor Cache
- Shared Pool

---

### XA transaction pressure

Simulates distributed transaction instability by:

- Running concurrent XA transactions
- Mixing commit and rollback patterns
- Generating heavy DML workload
- Interrupting sessions mid-transaction

This reproduces scenarios such as:

- XA rollback failures
- Transaction inconsistency risk
- Resource cleanup pressure

---

### Chaos injection

Failure is triggered by:

- Periodic session termination
- Shared pool flush
- Concurrent memory pressure

This simulates:

- Node instability
- Application crashes
- Network interruptions
- Resource exhaustion

---

## Test Architecture

```

Thread Model:

60 total threads

30 XA transaction threads
30 memory pressure threads

Additional chaos thread:

* Session kill
* Shared pool flush

```

Workload interaction:

```

OOM pressure → memory stress

XA workload → transaction stress

Session kill → failure trigger

Shared pool flush → cache disruption

```

---

## Test Workflow

### Step 1 Database initialization

Creates:

XA_SOURCE_TABLE → seed data  
XA_TARGET_TABLE → transaction target  

Populates test data.

---

### Step 2 Memory pressure threads

Each thread randomly performs:

DDL churn:

CREATE TABLE  
DROP TABLE  

SQL parse storm:

Dynamic SQL execution

PL/SQL pressure:

Anonymous block execution

Dictionary churn:

Sequence create/drop

Cursor pressure:

Repeated cursor execution

---

### Step 3 XA workload

XA threads perform:

Insert workload:

Insert sampled data

Update workload:

Update inserted rows

Transaction mix:

50% commit  
50% rollback

---

### Step 4 Failure injection

Chaos thread periodically:

Kills active sessions

Flushes shared pool

This forces:

Transaction interruption  
Cache invalidation  
Resource cleanup pressure  

---

## Execution

Example environment variables:

```

DB_NODE1=jdbc:tibero:thin:@node1:port:db
DB_NODE2=jdbc:tibero:thin:@node2:port:db

APP_USER=test
APP_PASSWORD=test

OOM_USER=oom
OOM_PASSWORD=oom

SYS_USER=sys
SYS_PASSWORD=sys

```

Run:

```

java DatabaseOOMFailureTest

```

---

## Expected Behavior

Database should:

Maintain transactional consistency

Handle session termination safely

Recover XA transactions correctly

Avoid memory corruption

Handle cache invalidation safely

Release resources properly

---

## Failure Signals to Observe

Potential instability indicators:

Session termination errors

XA rollback anomalies

Memory allocation failures

Cache latch contention

Unexpected transaction aborts

Internal errors

---

## Engineering Observations

This scenario is useful for studying:

Database behavior under combined failure conditions

Memory pressure handling

XA recovery behavior

Session cleanup mechanisms

Shared pool stability

---

## Engineering Purpose

This test is designed as a reliability engineering exercise to study:

How databases behave under stress

Failure handling correctness

Resource cleanup behavior

Transaction safety guarantees

It is intended for:

Reliability engineering study

Database failure testing

Chaos engineering practice

---

## Notes

This test intentionally introduces instability.

Do not run in production environments.

Use isolated test systems only.

---

## Author Notes

This scenario is based on real-world production troubleshooting patterns involving:

Memory pressure

Distributed transactions

Session instability

Cache pressure

The goal is to better understand database reliability characteristics under failure conditions.
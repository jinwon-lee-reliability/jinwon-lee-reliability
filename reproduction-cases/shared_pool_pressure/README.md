# Database Failure Reproduction Lab

## Overview

This project was created to simulate database failure scenarios
to support incident investigation and reliability testing.

The goal was to reproduce complex failure conditions such as:

- Memory pressure scenarios
- Transaction rollback behavior
- Concurrent workload stress
- Restart recovery validation

This project focuses on **failure engineering methodology**
rather than production deployment.

---

## Problem

In production environments, database internal errors or instability
often cannot be reproduced easily.

Without reproducible scenarios:

- Root cause analysis becomes difficult
- Fix validation is unreliable
- Reliability risk remains unknown

This project was built to create structured failure scenarios
to improve reproducibility.

---

## Approach

A scenario runner was built to simulate:

- Parallel transaction workloads
- Insert/update stress
- Rollback timing issues
- Large join pressure
- Database restart recovery behavior

The focus was:

**Reproduce → Observe → Learn**

---

## Architecture

Scenario Runner (Java)
→ Parallel workload generator
→ Failure scenario injection
→ Restart automation
→ Logging and result analysis

---

## Project Structure

shared_pool_pressure/

ScenarioRunner.java
LogUtil.java

scenario_runner.sh
restart_db.sh

sql/
setup_test_objects.sql


---

## Key Techniques Used

Engineering techniques demonstrated:

- Parallel session workload generation
- Failure scenario orchestration
- Transaction rollback simulation
- Restart recovery testing
- Resource pressure simulation

---

## Lessons Learned

Key insights from building this lab:

- Memory failures are rarely caused by a single factor
- Restart timing significantly affects behavior
- Reproducibility requires structured scenario design
- Reliability testing requires controlled failure injection

---

## Reliability Engineering Concepts Demonstrated

This project demonstrates practical reliability engineering thinking:

- Failure reproduction
- Stress testing
- Incident simulation
- Recovery validation
- Scenario based testing

---

## How to Run (Example)

Environment setup required:

- Database instance
- JDBC driver
- Test schema

Example execution:


./scenario_runner.sh


Note:

Environment configuration must be updated before execution.

---

## Disclaimer

This project is a generalized reliability testing example.

All environment specific information has been removed.

The purpose is to demonstrate reliability engineering techniques,
not production usage.

---

## Author

Reliability engineering focused database engineer
with experience in production incident analysis
and failure reproduction methodology.
# Allocator Pressure Reproduction Lab

## Overview

This project reproduces database allocator pressure scenarios using synthetic workloads.

The goal is to simulate failure conditions that can occur under heavy concurrency,
metadata churn, and transaction allocation pressure.

This lab was built to study reliability behavior under stress conditions and to
practice failure reproduction techniques commonly required in SRE roles.

---

## Failure Patterns Simulated

This reproduction focuses on several allocator pressure patterns:

• Savepoint object churn  
• Autonomous transaction allocation pressure  
• Session connection storms  
• Metadata lookup pressure  

These patterns can stress:

• Shared memory allocators  
• Session management paths  
• Transaction allocation paths  
• Metadata structures  

---

## Scenario Design

The reproduction combines multiple synthetic workload generators:

### Savepoint churn

Repeated savepoint creation and rollback patterns to simulate
transaction object allocation pressure.

### Transaction pressure

Autonomous transaction loops generating frequent allocation and cleanup cycles.

### Session storm

Concurrent connection bursts to simulate login storms and session allocator pressure.

### Metadata polling

Lightweight repeated SGA queries to simulate metadata lookup contention.

---

## Usage

Run default scenario:


./run_allocator_repro.sh


Run full pressure scenario:


MODE=full_pressure ./run_allocator_repro.sh


Run session storm only:


MODE=session_storm ./run_allocator_repro.sh


Run transaction pressure only:


MODE=tx_pressure ./run_allocator_repro.sh


Stop workloads:


MODE=stop ./run_allocator_repro.sh


---

## Configuration

Environment variables:


DB_USER=TEST_USER
DB_PW=TEST_PASS
DB_ALIAS=TEST_DB

PROCS=20
DURATION_SEC=900


Example:


DB_USER=test DB_PW=test MODE=full_pressure ./run_allocator_repro.sh


---

## Project Goals

This project focuses on developing skills in:

• Failure reproduction engineering  
• Reliability testing methodology  
• Synthetic workload design  
• Database stress scenario modeling  
• Incident analysis preparation  

---

## Reliability Engineering Context

Failure reproduction is a critical skill in reliability engineering.

Rather than only reacting to incidents, SREs often need to:

Reproduce allocator contention  
Simulate concurrency failures  
Stress internal execution paths  
Validate stability under pressure  

This project reflects that mindset.

---

## Design Principles

This project follows these principles:

• No production schema used  
• Synthetic data only  
• Generic naming  
• Reproducible environment  
• No business logic included  

---

## Learning Outcomes

Through this project I practiced:

Designing synthetic failure scenarios  
Building reproducible stress environments  
Understanding allocator pressure behavior  
Improving incident debugging intuition  

---

## Notes

This is a synthetic reproduction lab intended only for testing and learning purposes.

It should never be executed on production systems.

---

## Future Improvements

Possible extensions:

• Failure detection automation  
• Stress metrics collection  
• Chaos testing integration  
• Automated failure validation  
• Resource usage dashboards

---

## Author

Reliability engineering study project focused on database failure reproduction
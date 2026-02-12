# Case Study 02  
## Cost Reduction Pressure and Cluster Architecture Validation in a National-Scale System

### Background

Two to three years after the COVID-driven traffic explosion, user volume declined significantly.

However, the infrastructure remained provisioned across 12 regional HA deployments,  
and the core resource footprint remained high.

From the customer’s perspective (national education platform operator),  
infrastructure cost reduction became a primary objective.

The technical challenge shifted from **scaling under extreme load**  
to **downsizing safely without compromising architectural integrity**.

---

### Proposed Direction

The following approaches were discussed:

- Regional consolidation
- Transition to TAC (cluster-based architecture)
- Exploration of TSC (network-synchronized standby)
- More elastic scale-in / scale-out strategies

However, within the cloud provider environment,  
**shared storage was limited to NAS-based systems only.**

Block-level shared storage was not available.

This constraint became central to the architectural discussion.

---

### Key Technical Questions

#### 1. TAC on NAS-Based Shared Storage

In a NAS-based cluster configuration:

- I/O throughput contention becomes critical
- Synchronization overhead increases
- Network latency directly affects database commit performance
- Shared storage becomes a dominant scaling bottleneck

The question was not theoretical scalability —  
but whether OLTP consistency and throughput could be maintained  
under production-grade concurrency.

---

#### 2. TSC Purpose Alignment

TSC is designed primarily for:

- Disaster Recovery
- Data synchronization across nodes

It is not architected for high-throughput OLTP scaling.

Using standby nodes for active read scaling or load distribution:

- Increases synchronization complexity
- Introduces operational ambiguity
- Blurs the boundary between DR and active workload distribution

Architecture intent and operational use case must align.

---

### Validation Approach

Rather than blocking architectural change by opinion,  
we proceeded with structured performance validation.

We executed benchmark tests including TiberoZeta  
(Exadata-like architecture validation scenario).

#### 1500 Session TPC-C Test Results

| Architecture | tpmC |
|--------------|------|
| Single Node  | 49,813 |
| Zeta Cluster | 18,821 |

Observations:

- Under moderate concurrency, clustered configuration showed minor benefits.
- Under high session density, synchronization overhead dominated.
- Throughput dropped to ~40% of single-node performance.
- Shared storage and inter-node sync became primary bottlenecks.

The conclusion was data-driven:

**Cluster configuration on NAS-based shared storage degraded OLTP throughput at scale.**

---

### Decision

- Immediate TAC/NAS migration was deferred.
- TSC production activation was not adopted.
- Active-Standby architecture was maintained.
- Additional validation was encouraged before structural change.

---

### Long-Term Outcome

Over time, the platform transitioned toward an OpenSQL (PostgreSQL-based) architecture.

This was not a defensive posture against cost reduction.

It was a structured, validation-driven architectural decision process  
under infrastructure constraints and financial pressure.

---

### Key Lessons

- Disaster Recovery architecture is not equivalent to OLTP scaling.
- Shared storage design determines cluster viability.
- Documentation-level features must not bypass production validation.
- Cost reduction must follow architectural integrity, not precede it.
- Data-backed decisions reduce long-term operational risk.

---

### My Role

- Led technical evaluation and performance validation.
- Owned benchmark execution and analysis.
- Defended production architecture through data-driven argument.
- Coordinated cross-team technical discussions under executive visibility.
- Balanced reliability, cost pressure, and architectural constraints.

---

### Reflection

This case reinforced my core principle:

> Reliability architecture must be validated under realistic concurrency conditions.
>  
> Scaling down safely is often harder than scaling up.
>  
> Architecture decisions must align with storage reality, not theoretical capability.

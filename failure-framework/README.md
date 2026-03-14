Database Failure Testing Framework

A lightweight framework for reproducing database reliability issues.

Features:

Pluggable scenario execution
XA transaction support
Concurrent workload testing
Custom SQL scenario injection

Example:

Scenario scenario = ctx -> {

ctx.sql("insert into test values(1)");

ctx.sql("update test set col=2");

};

FailureFramework.run(ds, scenario);



\## Why this project exists



During database troubleshooting work (XA transactions, memory pressure, shared pool issues),

I noticed many failure reproduction scenarios had similar execution patterns.



To reduce duplicated test code and enable flexible scenario testing,

I built a lightweight failure testing framework.


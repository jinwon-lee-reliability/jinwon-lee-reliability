\## Example



Scenario scenario = ctx -> {



&#x20;   ctx.sql("insert into test values(1)");

&#x20;   ctx.sql("update test set col=2");



};



FailureFramework.run(ds, scenario);


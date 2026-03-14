package core;

@FunctionalInterface
public interface Scenario {

    void run(ScenarioContext ctx) throws Exception;

}
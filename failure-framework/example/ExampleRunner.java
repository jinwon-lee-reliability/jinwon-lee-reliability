package example;

import core.FailureFramework;
import core.Scenario;
import datasource.XADataSourceFactory;

import javax.sql.XADataSource;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ExampleRunner {

    public static void main(
            String[] args){

        XADataSource ds =
                XADataSourceFactory.create();

        Scenario scenario = ctx -> {

            ctx.sql(
            "insert into test values(1)"
            );

            ctx.sql(
            "update test set col=2"
            );

        };

        ExecutorService executor =
                Executors.newFixedThreadPool(10);

        for(int i=0;i<10;i++){

            executor.submit(() ->
                    FailureFramework.run(
                            ds,
                            scenario
                    )
            );

        }

    }

}
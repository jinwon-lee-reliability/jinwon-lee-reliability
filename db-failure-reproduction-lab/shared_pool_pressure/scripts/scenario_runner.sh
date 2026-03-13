# Database Reliability Failure Simulation Lab
# Replace environment configuration before execution

#!/bin/ksh

# Set scenario names
scenario_name_1="19"
scenario_name_2="20"

# Set initial values for the database URLs, ports, and service name
db_url1="DB_NODE1"
db_url2="DB_NODE2"
service_name="TAC"
port_node1_scenario1=47100
port_node2_scenario1=47200
port_node1_scenario2=57100
port_node2_scenario2=57200


# Loop through different configurations of parallel sessions and total sessions
set -A configurations "60 100" "100 500" "150 1000"

with_timeout() {
  command=$1
  timeout=$2

  # 백그라운드에서 명령어 실행
  eval "$command" &
  command_pid=$!

  # 타임아웃 시간만큼 기다림
  wait $command_pid & sleep $timeout

  # 명령어가 아직 실행 중이면 종료
  if ps -p $command_pid > /dev/null; then
    echo "Command timed out. Killing process..."
    kill $command_pid
  fi
}


# Loop through the scenarios and configurations
for i in 1 2 3 4 5; do
  for config in "${configurations[@]}"; do
    parallel_sessions=$(echo $config | awk '{print $1}')
    total_sessions=$(echo $config | awk '{print $2}')

    echo "Restarting database instance"
    ksh restart_db.sh tibero7_19 >> restart_db.log 2>&1
    echo "Running scenario $scenario_name_1 with parallel_sessions=$parallel_sessions and total_sessions=$total_sessions"

    # Run the Java program for Scenario 1
    with_timeout "java -Xmx4G -cp .:./lib/jdbc-driver.jar ScenarioRunner $db_url1 $port_node1_scenario1 $db_url2 $port_node2_scenario1 $service_name $parallel_sessions $total_sessions ${scenario_name_1}_${parallel_sessions}" 14400

    echo "Restarting db tibero7_20"
    ksh restart_db.sh tibero7_20 >> restart_db.log 2>&1
    echo "Running scenario $scenario_name_2 with parallel_sessions=$parallel_sessions and total_sessions=$total_sessions"

    # Run the Java program for Scenario 2
    with_timeout "java -Xmx4G -cp .:./lib/jdbc-driver.jar ScenarioRunner $db_url1 $port_node1_scenario2 $db_url2 $port_node2_scenario2 $service_name $parallel_sessions $total_sessions ${scenario_name_2}_${parallel_sessions}" 14400
  done
done

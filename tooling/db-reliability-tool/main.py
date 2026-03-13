from src.runner import run_sql_file
from src.parser import parse_single_number
from src.detector import evaluate_session_count
from src.report import print_report


def main():

    raw = run_sql_file("sql/session_count.sql")

    value = parse_single_number(raw)

    status = evaluate_session_count(value)

    print_report(value, status)


if __name__ == "__main__":
    main()
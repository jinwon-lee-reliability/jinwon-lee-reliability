import subprocess


def run_sql_file(sql_path: str):

    cmd = f"tbsql sys/tibero @{sql_path}"

    result = subprocess.run(
        cmd,
        shell=True,
        capture_output=True,
        text=True
    )

    if result.returncode != 0:
        return None

    return result.stdout.strip()
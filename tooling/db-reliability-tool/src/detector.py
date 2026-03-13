def evaluate_session_count(value):

    if value is None:
        return "UNKNOWN"

    if value > 500:
        return "CRITICAL"

    if value > 300:
        return "WARNING"

    return "OK"
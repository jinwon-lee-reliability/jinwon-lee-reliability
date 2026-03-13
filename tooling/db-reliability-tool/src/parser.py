def parse_single_number(output):

    try:
        value = int(output.split()[-1])
        return value

    except:
        return None
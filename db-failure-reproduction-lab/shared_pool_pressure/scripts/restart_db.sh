#!/bin/ksh

# 인자 체크
if [ $# -ne 1 ]; then
    print "사용법: $0 <function_name>"
    exit 1
fi

# 입력받은 함수 이름
FUNCTION=$1

# expect 스크립트 호출
/usr/bin/expect -f restart_db.exp $FUNCTION

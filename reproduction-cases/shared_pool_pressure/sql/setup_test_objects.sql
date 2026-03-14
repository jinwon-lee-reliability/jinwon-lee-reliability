
--  Tibero7_17_1

create database
  user sys identified by tibero
  NOARCHIVELOG
  MAXLOGFILES 255
  MAXLOGMEMBERS 8
  MAXDATAFILES 4096
  MAXARCHIVELOG 500
  MAXBACKUPSET 500
  MAXLOGHISTORY 500
  CHARACTER SET UTF8
  NATIONAL CHARACTER SET UTF16
  logfile
      group 1 ('/dev/rr1g050') size 1g,
      group 2 ('/dev/rr1g051') size 1g,
      group 3 ('/dev/rr1g052') size 1g
  datafile '/dev/rr32g021' size 32g
  SYSSUB
    datafile '/dev/rr32g022' size 32g
  default temporary tablespace TEMP
    tempfile '/dev/rr32g023' size 32g
  undo tablespace UNDO0
    datafile '/dev/rr32g024' size 32g
  default tablespace USR
    datafile  '/dev/rr32g025' size 32g
;

-- Tibero7_17_2

/* ######## TAC Add Node Sample ######## */

create undo tablespace UNDO1
datafile '/dev/rr32g025' size 32G
;

alter database add logfile thread 1 group 4 '/dev/rr1g053' size 1g;
alter database add logfile thread 1 group 5 '/dev/rr1g054' size 1g;
alter database add logfile thread 1 group 6 '/dev/rr1g055' size 1g;

alter database enable public thread 1;



--  Tibero7_19_1

create database
  user sys identified by tibero
  NOARCHIVELOG
  MAXLOGFILES 255
  MAXLOGMEMBERS 8
  MAXDATAFILES 4096
  MAXARCHIVELOG 500
  MAXBACKUPSET 500
  MAXLOGHISTORY 500
  CHARACTER SET UTF8
  NATIONAL CHARACTER SET UTF16
  logfile
      group 1 ('/dev/rr1g056') size 1g,
      group 2 ('/dev/rr1g057') size 1g,
      group 3 ('/dev/rr1g058') size 1g
  datafile '/dev/rr32g026' size 32g
  SYSSUB
    datafile '/dev/rr32g027' size 32g
  default temporary tablespace TEMP
    tempfile '/dev/rr32g028' size 32g
  undo tablespace UNDO0
    datafile '/dev/rr32g029' size 32g
  default tablespace USR
    datafile  '/dev/rr32g030' size 32g
;

-- Tibero7_19_2

/* ######## TAC Add Node Sample ######## */

create undo tablespace UNDO1
datafile '/dev/rr32g031' size 32G;

alter database add logfile thread 1 group 4 '/dev/rr1g059' size 1g;
alter database add logfile thread 1 group 5 '/dev/rr1g060' size 1g;
alter database add logfile thread 1 group 6 '/dev/rr1g061' size 1g;

alter database enable public thread 1;





--  Tibero7_20_1

create database
  user sys identified by tibero
  NOARCHIVELOG
  MAXLOGFILES 255
  MAXLOGMEMBERS 8
  MAXDATAFILES 4096
  MAXARCHIVELOG 500
  MAXBACKUPSET 500
  MAXLOGHISTORY 500
  CHARACTER SET UTF8
  NATIONAL CHARACTER SET UTF16
  logfile
      group 1 ('/dev/rr1g062') size 1g,
      group 2 ('/dev/rr1g063') size 1g,
      group 3 ('/dev/rr1g064') size 1g
  datafile '/dev/rr32g032' size 32g
  SYSSUB
    datafile '/dev/rr32g033' size 32g
  default temporary tablespace TEMP
    tempfile '/dev/rr32g034' size 32g
  undo tablespace UNDO0
    datafile '/dev/rr32g035' size 32g
  default tablespace USR
    datafile  '/dev/rr32g036' size 32g
;

-- Tibero7_20_2

/* ######## TAC Add Node Sample ######## */

create undo tablespace UNDO1
datafile '/dev/rr32g037' size 32G
;

alter database add logfile thread 1 group 4 '/dev/rr1g065' size 1g;
alter database add logfile thread 1 group 5 '/dev/rr1g066' size 1g;
alter database add logfile thread 1 group 6 '/dev/rr1g067' size 1g;

alter database enable public thread 1;
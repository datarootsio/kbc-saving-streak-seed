      *================================================================*
      * COPYBOOK : DBAREA                                              *
      * PURPOSE  : DB2-STYLE ACCOUNT DATA ACCESS & AUDIT LOG COMMAREAS *
      *================================================================*
       01  DB-ACCESS-AREA.
           05  DB-OPERATION            PIC X(08).
               88  DB-OP-INS-ACCT      VALUE 'INS-ACCT'.
               88  DB-OP-FND-ACCT      VALUE 'FND-ACCT'.
               88  DB-OP-CNT-SUB       VALUE 'CNT-SUB '.
               88  DB-OP-INS-SUB       VALUE 'INS-SUB '.
           05  DB-SQLCODE              PIC S9(04) COMP.
               88  DB-SQL-OK           VALUE 0.
               88  DB-SQL-NOT-FOUND    VALUE +100.
               88  DB-SQL-DUP-KEY      VALUE -803.
               88  DB-SQL-UNAVAIL      VALUE -904.
           05  DB-ACCT-DATA.
               10  DB-ACCT-IBAN        PIC X(16).
               10  DB-ACCT-REQ-ID      PIC X(12).
               10  DB-ACCT-CUSTOMER-ID PIC X(10).
               10  DB-ACCT-TYPE        PIC X(08).
               10  DB-ACCT-CURRENCY    PIC X(03).
               10  DB-ACCT-BALANCE     PIC 9(09)V99.
               10  DB-ACCT-STATUS      PIC X(08).
                   88  DB-ACCT-ACTIVE  VALUE 'ACTIVE  '.
                   88  DB-ACCT-FROZEN  VALUE 'FROZEN  '.
                   88  DB-ACCT-CLOSED  VALUE 'CLOSED  '.
               10  DB-ACCT-CHANNEL     PIC X(08).
           05  DB-SUB-DATA.
               10  DB-SUB-IBAN         PIC X(16).
               10  DB-SUB-PARENT-IBAN  PIC X(16).
               10  DB-SUB-REQ-ID       PIC X(12).
               10  DB-SUB-CUSTOMER-ID  PIC X(10).
               10  DB-SUB-CURRENCY     PIC X(03).
               10  DB-SUB-BALANCE      PIC 9(09)V99.
               10  DB-SUB-STATUS       PIC X(08).
               10  DB-SUB-PURPOSE      PIC X(12).
               10  DB-SUB-ACTIVE-CNT   PIC 9(04) COMP.
               10  DB-SUB-TOTAL-CNT    PIC 9(04) COMP.

       01  AUDIT-LOG-AREA.
           05  AUD-REQ-ID              PIC X(12).
           05  AUD-OPERATION           PIC X(10).
           05  AUD-CHANNEL             PIC X(08).
           05  AUD-CUSTOMER-ID         PIC X(10).
           05  AUD-REQUESTER-ID        PIC X(10).
           05  AUD-MANDATE             PIC X(12).
           05  AUD-RISK-STATUS         PIC X(14).
           05  AUD-RETURN-CODE         PIC X(02).
           05  AUD-REASON-CODE         PIC X(04).
           05  AUD-ALLOCATED-IBAN      PIC X(16).
           05  AUD-WRITE-STATUS        PIC X(08).
               88  AUD-RECORDED        VALUE 'RECORDED'.
               88  AUD-FAILED          VALUE 'AUD-FAIL'.

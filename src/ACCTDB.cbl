      *================================================================*
      * PROGRAM  : ACCTDB                                              *
      * PURPOSE  : DB2-STYLE ACCOUNT & SUB-ACCOUNT DATA ACCESS MODULE  *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. ACCTDB IS INITIAL.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-FILE ASSIGN TO EXTERNAL DD_ACCTFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-ACCT-FS.
           SELECT SUB-FILE ASSIGN TO EXTERNAL DD_SUBFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-SUB-FS.

       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-FILE.
       01  ACCT-FILE-REC.
           05  AF-IBAN                 PIC X(16).
           05  AF-SEP1                 PIC X(01).
           05  AF-REQ-ID               PIC X(12).
           05  AF-SEP2                 PIC X(01).
           05  AF-CUSTOMER-ID          PIC X(10).
           05  AF-SEP3                 PIC X(01).
           05  AF-TYPE                 PIC X(08).
           05  AF-SEP4                 PIC X(01).
           05  AF-CURRENCY             PIC X(03).
           05  AF-SEP5                 PIC X(01).
           05  AF-BALANCE              PIC 9(09)V99.
           05  AF-SEP6                 PIC X(01).
           05  AF-STATUS               PIC X(08).
           05  AF-SEP7                 PIC X(01).
           05  AF-CHANNEL              PIC X(08).

       FD  SUB-FILE.
       01  SUB-FILE-REC.
           05  SF-IBAN                 PIC X(16).
           05  SF-SEP1                 PIC X(01).
           05  SF-PARENT-IBAN          PIC X(16).
           05  SF-SEP2                 PIC X(01).
           05  SF-REQ-ID               PIC X(12).
           05  SF-SEP3                 PIC X(01).
           05  SF-CUSTOMER-ID          PIC X(10).
           05  SF-SEP4                 PIC X(01).
           05  SF-CURRENCY             PIC X(03).
           05  SF-SEP5                 PIC X(01).
           05  SF-BALANCE              PIC 9(09)V99.
           05  SF-SEP6                 PIC X(01).
           05  SF-STATUS               PIC X(08).
           05  SF-SEP7                 PIC X(01).
           05  SF-PURPOSE              PIC X(12).

       WORKING-STORAGE SECTION.
       01  WS-ACCT-FS                  PIC X(02) VALUE SPACES.
       01  WS-SUB-FS                   PIC X(02) VALUE SPACES.
       01  WS-ENV-ACCT                 PIC X(256) VALUE SPACES.
       01  WS-ENV-SUB                  PIC X(256) VALUE SPACES.
       01  WS-EOF-FLAG                 PIC X(01) VALUE 'N'.
           88  WS-EOF                  VALUE 'Y'.
       01  WS-FOUND-FLAG               PIC X(01) VALUE 'N'.
           88  WS-FOUND                VALUE 'Y'.
       01  WS-CHK-REQ-ID               PIC X(12) VALUE SPACES.
       01  WS-CHK-IBAN                 PIC X(16) VALUE SPACES.

       LINKAGE SECTION.
       COPY DBAREA.

       PROCEDURE DIVISION USING DB-ACCESS-AREA.
       0000-MAIN.
           MOVE 0 TO DB-SQLCODE
           ACCEPT WS-ENV-ACCT FROM ENVIRONMENT 'DD_ACCTFILE'
           ACCEPT WS-ENV-SUB  FROM ENVIRONMENT 'DD_SUBFILE'

           EVALUATE TRUE
               WHEN DB-OP-INS-ACCT
                   PERFORM 1000-INSERT-ACCT
               WHEN DB-OP-FND-ACCT
                   PERFORM 2000-FIND-ACCT
               WHEN DB-OP-CNT-SUB
                   PERFORM 3000-COUNT-SUB
               WHEN DB-OP-INS-SUB
                   PERFORM 4000-INSERT-SUB
               WHEN OTHER
                   MOVE -904 TO DB-SQLCODE
           END-EVALUATE

           GOBACK.

       1000-INSERT-ACCT.
           IF WS-ENV-ACCT = SPACES
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           MOVE DB-ACCT-REQ-ID TO WS-CHK-REQ-ID
           MOVE DB-ACCT-IBAN   TO WS-CHK-IBAN
           PERFORM 8000-CHECK-DUPLICATE
           IF NOT DB-SQL-OK
               EXIT PARAGRAPH
           END-IF

           OPEN EXTEND ACCT-FILE
           IF WS-ACCT-FS = '35'
               OPEN OUTPUT ACCT-FILE
           END-IF
           IF WS-ACCT-FS NOT = '00'
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           MOVE SPACES              TO ACCT-FILE-REC
           MOVE DB-ACCT-IBAN        TO AF-IBAN
           MOVE '|'                 TO AF-SEP1
           MOVE DB-ACCT-REQ-ID      TO AF-REQ-ID
           MOVE '|'                 TO AF-SEP2
           MOVE DB-ACCT-CUSTOMER-ID TO AF-CUSTOMER-ID
           MOVE '|'                 TO AF-SEP3
           MOVE DB-ACCT-TYPE        TO AF-TYPE
           MOVE '|'                 TO AF-SEP4
           MOVE DB-ACCT-CURRENCY    TO AF-CURRENCY
           MOVE '|'                 TO AF-SEP5
           MOVE DB-ACCT-BALANCE     TO AF-BALANCE
           MOVE '|'                 TO AF-SEP6
           MOVE DB-ACCT-STATUS      TO AF-STATUS
           MOVE '|'                 TO AF-SEP7
           MOVE DB-ACCT-CHANNEL     TO AF-CHANNEL
           WRITE ACCT-FILE-REC
           IF WS-ACCT-FS NOT = '00'
               MOVE -904 TO DB-SQLCODE
           END-IF
           CLOSE ACCT-FILE.

       2000-FIND-ACCT.
           IF WS-ENV-ACCT = SPACES
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           OPEN INPUT ACCT-FILE
           IF WS-ACCT-FS = '35'
               MOVE +100 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF
           IF WS-ACCT-FS NOT = '00'
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           MOVE 'N' TO WS-EOF-FLAG
           MOVE 'N' TO WS-FOUND-FLAG
           PERFORM UNTIL WS-EOF OR WS-FOUND
               READ ACCT-FILE
                   AT END
                       SET WS-EOF TO TRUE
                   NOT AT END
                       IF AF-IBAN = DB-ACCT-IBAN
                           MOVE AF-REQ-ID      TO DB-ACCT-REQ-ID
                           MOVE AF-CUSTOMER-ID TO DB-ACCT-CUSTOMER-ID
                           MOVE AF-TYPE        TO DB-ACCT-TYPE
                           MOVE AF-CURRENCY    TO DB-ACCT-CURRENCY
                           MOVE AF-BALANCE     TO DB-ACCT-BALANCE
                           MOVE AF-STATUS      TO DB-ACCT-STATUS
                           MOVE AF-CHANNEL     TO DB-ACCT-CHANNEL
                           SET WS-FOUND TO TRUE
                       END-IF
               END-READ
           END-PERFORM
           CLOSE ACCT-FILE

           IF NOT WS-FOUND
               MOVE +100 TO DB-SQLCODE
           END-IF.

       3000-COUNT-SUB.
           MOVE 0 TO DB-SUB-ACTIVE-CNT
           MOVE 0 TO DB-SUB-TOTAL-CNT
           IF WS-ENV-SUB = SPACES
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           OPEN INPUT SUB-FILE
           IF WS-SUB-FS = '35'
               MOVE 0 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF
           IF WS-SUB-FS NOT = '00'
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           MOVE 'N' TO WS-EOF-FLAG
           PERFORM UNTIL WS-EOF
               READ SUB-FILE
                   AT END
                       SET WS-EOF TO TRUE
                   NOT AT END
                       IF SF-PARENT-IBAN = DB-SUB-PARENT-IBAN
                           ADD 1 TO DB-SUB-TOTAL-CNT
                           IF SF-STATUS = 'ACTIVE  '
                               ADD 1 TO DB-SUB-ACTIVE-CNT
                           END-IF
                       END-IF
               END-READ
           END-PERFORM
           CLOSE SUB-FILE.

       4000-INSERT-SUB.
           IF WS-ENV-SUB = SPACES
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           MOVE DB-SUB-REQ-ID TO WS-CHK-REQ-ID
           MOVE DB-SUB-IBAN   TO WS-CHK-IBAN
           PERFORM 8000-CHECK-DUPLICATE
           IF NOT DB-SQL-OK
               EXIT PARAGRAPH
           END-IF

           OPEN EXTEND SUB-FILE
           IF WS-SUB-FS = '35'
               OPEN OUTPUT SUB-FILE
           END-IF
           IF WS-SUB-FS NOT = '00'
               MOVE -904 TO DB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           MOVE SPACES             TO SUB-FILE-REC
           MOVE DB-SUB-IBAN        TO SF-IBAN
           MOVE '|'                TO SF-SEP1
           MOVE DB-SUB-PARENT-IBAN TO SF-PARENT-IBAN
           MOVE '|'                TO SF-SEP2
           MOVE DB-SUB-REQ-ID      TO SF-REQ-ID
           MOVE '|'                TO SF-SEP3
           MOVE DB-SUB-CUSTOMER-ID TO SF-CUSTOMER-ID
           MOVE '|'                TO SF-SEP4
           MOVE DB-SUB-CURRENCY    TO SF-CURRENCY
           MOVE '|'                TO SF-SEP5
           MOVE DB-SUB-BALANCE     TO SF-BALANCE
           MOVE '|'                TO SF-SEP6
           MOVE DB-SUB-STATUS      TO SF-STATUS
           MOVE '|'                TO SF-SEP7
           MOVE DB-SUB-PURPOSE     TO SF-PURPOSE
           WRITE SUB-FILE-REC
           IF WS-SUB-FS NOT = '00'
               MOVE -904 TO DB-SQLCODE
           END-IF
           CLOSE SUB-FILE.

       8000-CHECK-DUPLICATE.
           IF WS-ENV-ACCT NOT = SPACES
               OPEN INPUT ACCT-FILE
               IF WS-ACCT-FS = '00'
                   MOVE 'N' TO WS-EOF-FLAG
                   PERFORM UNTIL WS-EOF OR DB-SQL-DUP-KEY
                       READ ACCT-FILE
                           AT END
                               SET WS-EOF TO TRUE
                           NOT AT END
                               IF AF-REQ-ID = WS-CHK-REQ-ID
                                  OR AF-IBAN = WS-CHK-IBAN
                                   MOVE -803 TO DB-SQLCODE
                               END-IF
                       END-READ
                   END-PERFORM
                   CLOSE ACCT-FILE
               ELSE
                   IF WS-ACCT-FS NOT = '35'
                       MOVE -904 TO DB-SQLCODE
                       EXIT PARAGRAPH
                   END-IF
               END-IF
           END-IF

           IF NOT DB-SQL-OK
               EXIT PARAGRAPH
           END-IF

           IF WS-ENV-SUB NOT = SPACES
               OPEN INPUT SUB-FILE
               IF WS-SUB-FS = '00'
                   MOVE 'N' TO WS-EOF-FLAG
                   PERFORM UNTIL WS-EOF OR DB-SQL-DUP-KEY
                       READ SUB-FILE
                           AT END
                               SET WS-EOF TO TRUE
                           NOT AT END
                               IF SF-REQ-ID = WS-CHK-REQ-ID
                                  OR SF-IBAN = WS-CHK-IBAN
                                   MOVE -803 TO DB-SQLCODE
                               END-IF
                       END-READ
                   END-PERFORM
                   CLOSE SUB-FILE
               END-IF
           END-IF.

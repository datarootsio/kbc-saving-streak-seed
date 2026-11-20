      *================================================================*
      * PROGRAM  : ACCTCLI                                             *
      * PURPOSE  : BATCH / TERMINAL CLI HARNESS FOR ACCTOPEN SERVICE   *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. ACCTCLI.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT REQ-STDIN ASSIGN TO KEYBOARD
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-IN-FS.

       DATA DIVISION.
       FILE SECTION.
       FD  REQ-STDIN.
       01  STDIN-LINE                  PIC X(120).

       WORKING-STORAGE SECTION.
       01  WS-IN-FS                    PIC X(02) VALUE SPACES.
       01  WS-EOF-FLAG                 PIC X(01) VALUE 'N'.
           88  WS-EOF                  VALUE 'Y'.
       01  WS-TRIM-LINE                PIC X(120) VALUE SPACES.
       01  WS-KEY                      PIC X(32) VALUE SPACES.
       01  WS-VAL                      PIC X(80) VALUE SPACES.
       01  WS-CLEAN-KEY                PIC X(32) VALUE SPACES.
       01  WS-CLEAN-VAL                PIC X(80) VALUE SPACES.

       COPY ACCTREQ.
       COPY ACCTRSP.

       PROCEDURE DIVISION.
       0000-MAIN.
           INITIALIZE ACCT-REQUEST
           INITIALIZE ACCT-RESPONSE
           MOVE 'OPEN-ACCT ' TO REQ-OPERATION
           MOVE 0            TO REQ-INIT-DEPOSIT

           OPEN INPUT REQ-STDIN
           IF WS-IN-FS = '00'
               PERFORM UNTIL WS-EOF
                   READ REQ-STDIN
                       AT END
                           SET WS-EOF TO TRUE
                       NOT AT END
                           PERFORM 1000-PARSE-LINE
                   END-READ
               END-PERFORM
               CLOSE REQ-STDIN
           END-IF

           CALL 'ACCTOPEN' USING ACCT-REQUEST ACCT-RESPONSE

           DISPLAY 'RC='          RSP-RETURN-CODE
           DISPLAY 'REASON='      RSP-REASON-CODE
           DISPLAY 'IBAN='
               FUNCTION TRIM(RSP-ALLOCATED-IBAN TRAILING)
           DISPLAY 'PARENT_IBAN='
               FUNCTION TRIM(RSP-PARENT-IBAN TRAILING)
           DISPLAY 'AUDIT='
               FUNCTION TRIM(RSP-AUDIT-STATUS TRAILING)
           DISPLAY 'MESSAGE='
               FUNCTION TRIM(RSP-MESSAGE TRAILING)
           STOP RUN.

       1000-PARSE-LINE.
           MOVE FUNCTION TRIM(STDIN-LINE LEADING) TO WS-TRIM-LINE
           IF WS-TRIM-LINE = SPACES
               EXIT PARAGRAPH
           END-IF
           IF WS-TRIM-LINE(1:1) = '#'
               EXIT PARAGRAPH
           END-IF

           MOVE SPACES TO WS-KEY
           MOVE SPACES TO WS-VAL
           UNSTRING WS-TRIM-LINE DELIMITED BY '='
               INTO WS-KEY WS-VAL
           END-UNSTRING

           MOVE FUNCTION UPPER-CASE(FUNCTION TRIM(WS-KEY))
             TO WS-CLEAN-KEY
           MOVE FUNCTION TRIM(WS-VAL) TO WS-CLEAN-VAL

           EVALUATE WS-CLEAN-KEY
               WHEN 'REQ_ID'
                   MOVE WS-CLEAN-VAL TO REQ-REQUEST-ID
               WHEN 'OP'
               WHEN 'OPERATION'
                   MOVE WS-CLEAN-VAL TO REQ-OPERATION
               WHEN 'CHANNEL'
                   MOVE WS-CLEAN-VAL TO REQ-CHANNEL
               WHEN 'CUSTOMER_ID'
                   MOVE WS-CLEAN-VAL TO REQ-CUSTOMER-ID
               WHEN 'REQUESTER_ID'
                   MOVE WS-CLEAN-VAL TO REQ-REQUESTER-ID
               WHEN 'ACCOUNT_TYPE'
                   MOVE WS-CLEAN-VAL TO REQ-ACCOUNT-TYPE
               WHEN 'CURRENCY'
                   MOVE WS-CLEAN-VAL TO REQ-CURRENCY
               WHEN 'DEPOSIT'
                   IF WS-CLEAN-VAL NOT = SPACES
                       IF FUNCTION TEST-NUMVAL(WS-CLEAN-VAL) = 0
                           COMPUTE REQ-INIT-DEPOSIT =
                               FUNCTION NUMVAL(WS-CLEAN-VAL)
                       ELSE
                           MOVE -1 TO REQ-INIT-DEPOSIT
                       END-IF
                   END-IF
               WHEN 'PARENT_IBAN'
                   MOVE WS-CLEAN-VAL TO REQ-PARENT-IBAN
               WHEN 'SUB_PURPOSE'
                   MOVE WS-CLEAN-VAL TO REQ-SUB-PURPOSE
               WHEN OTHER
                   CONTINUE
           END-EVALUATE.

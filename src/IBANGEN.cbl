      *================================================================*
      * PROGRAM  : IBANGEN                                             *
      * PURPOSE  : BELGIAN KBC IBAN ALLOCATOR (ISO 13616 / MOD-97)     *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. IBANGEN IS INITIAL.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ACCT-SEQ-FILE ASSIGN TO EXTERNAL DD_ACCTFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-ACCT-FS.
           SELECT SUB-SEQ-FILE ASSIGN TO EXTERNAL DD_SUBFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-SUB-FS.

       DATA DIVISION.
       FILE SECTION.
       FD  ACCT-SEQ-FILE.
       01  ACCT-SEQ-REC                PIC X(120).
       FD  SUB-SEQ-FILE.
       01  SUB-SEQ-REC                 PIC X(120).

       WORKING-STORAGE SECTION.
       01  WS-ACCT-FS                  PIC X(02) VALUE SPACES.
       01  WS-SUB-FS                   PIC X(02) VALUE SPACES.
       01  WS-ENV-ACCT                 PIC X(256) VALUE SPACES.
       01  WS-ENV-SUB                  PIC X(256) VALUE SPACES.
       01  WS-EOF-FLAG                 PIC X(01) VALUE 'N'.
           88  WS-EOF                  VALUE 'Y'.
       01  WS-ACCT-CNT                 PIC 9(06) COMP-3 VALUE 0.
       01  WS-SUB-CNT                  PIC 9(06) COMP-3 VALUE 0.
       01  WS-BANK-CODE                PIC X(03) VALUE '736'.
       01  WS-SEQ-NUM                  PIC 9(07) VALUE 0.

       01  WS-BBAN-10-ALPHA.
           05  WS-B10-BANK             PIC X(03).
           05  WS-B10-SEQ              PIC 9(07).
       01  WS-BBAN-10-NUM REDEFINES WS-BBAN-10-ALPHA
                                       PIC 9(10).
       01  WS-QUOT-10                  PIC 9(10) COMP-3 VALUE 0.
       01  WS-REM-2                    PIC 9(02) COMP-3 VALUE 0.
       01  WS-NAT-CHK                  PIC 9(02) VALUE 0.

       01  WS-IBAN-18-ALPHA.
           05  WS-I18-BANK             PIC X(03).
           05  WS-I18-SEQ              PIC 9(07).
           05  WS-I18-NAT-CHK          PIC 9(02).
           05  WS-I18-BE00             PIC X(06).
       01  WS-IBAN-18-NUM REDEFINES WS-IBAN-18-ALPHA
                                       PIC 9(18).
       01  WS-QUOT-18                  PIC 9(18) COMP-3 VALUE 0.
       01  WS-IBAN-REM                 PIC 9(02) COMP-3 VALUE 0.
       01  WS-IBAN-CHK                 PIC 9(02) VALUE 0.

       LINKAGE SECTION.
       COPY EXTAREA.

       PROCEDURE DIVISION USING IBAN-GEN-AREA.
       0000-MAIN.
           MOVE '00'   TO IBAN-RETURN-CODE
           MOVE SPACES TO IBAN-VALUE

           IF IBAN-BANK-CODE NOT = SPACES
               MOVE IBAN-BANK-CODE TO WS-BANK-CODE
           END-IF
           IF WS-BANK-CODE IS NOT NUMERIC
               MOVE '12' TO IBAN-RETURN-CODE
               GOBACK
           END-IF

           PERFORM 1000-COUNT-RECORDS
           IF IBAN-RC-ERR
               GOBACK
           END-IF

           PERFORM 2000-BUILD-IBAN
           GOBACK.

       1000-COUNT-RECORDS.
           ACCEPT WS-ENV-ACCT FROM ENVIRONMENT 'DD_ACCTFILE'
           IF WS-ENV-ACCT = SPACES
               MOVE '12' TO IBAN-RETURN-CODE
               EXIT PARAGRAPH
           END-IF

           OPEN INPUT ACCT-SEQ-FILE
           IF WS-ACCT-FS = '00'
               MOVE 'N' TO WS-EOF-FLAG
               PERFORM UNTIL WS-EOF
                   READ ACCT-SEQ-FILE
                       AT END
                           SET WS-EOF TO TRUE
                       NOT AT END
                           ADD 1 TO WS-ACCT-CNT
                   END-READ
               END-PERFORM
               CLOSE ACCT-SEQ-FILE
           ELSE
               IF WS-ACCT-FS NOT = '35'
                   MOVE '12' TO IBAN-RETURN-CODE
                   EXIT PARAGRAPH
               END-IF
           END-IF

           ACCEPT WS-ENV-SUB FROM ENVIRONMENT 'DD_SUBFILE'
           IF IBAN-CLS-SUB AND WS-ENV-SUB = SPACES
               MOVE '12' TO IBAN-RETURN-CODE
               EXIT PARAGRAPH
           END-IF
           IF WS-ENV-SUB NOT = SPACES
               OPEN INPUT SUB-SEQ-FILE
               IF WS-SUB-FS = '00'
                   MOVE 'N' TO WS-EOF-FLAG
                   PERFORM UNTIL WS-EOF
                       READ SUB-SEQ-FILE
                           AT END
                               SET WS-EOF TO TRUE
                           NOT AT END
                               ADD 1 TO WS-SUB-CNT
                       END-READ
                   END-PERFORM
                   CLOSE SUB-SEQ-FILE
               ELSE
                   IF WS-SUB-FS NOT = '35'
                       MOVE '12' TO IBAN-RETURN-CODE
                       EXIT PARAGRAPH
                   END-IF
               END-IF
           END-IF.

       2000-BUILD-IBAN.
           IF IBAN-CLS-SUB
               COMPUTE WS-SEQ-NUM = 2000200 + WS-SUB-CNT + 1
           ELSE
               COMPUTE WS-SEQ-NUM = 1000200 + WS-ACCT-CNT + 1
           END-IF

           MOVE WS-BANK-CODE TO WS-B10-BANK
           MOVE WS-SEQ-NUM   TO WS-B10-SEQ
           DIVIDE WS-BBAN-10-NUM BY 97
               GIVING WS-QUOT-10 REMAINDER WS-REM-2
           IF WS-REM-2 = 0
               MOVE 97 TO WS-NAT-CHK
           ELSE
               MOVE WS-REM-2 TO WS-NAT-CHK
           END-IF

           MOVE WS-BANK-CODE TO WS-I18-BANK
           MOVE WS-SEQ-NUM   TO WS-I18-SEQ
           MOVE WS-NAT-CHK   TO WS-I18-NAT-CHK
           MOVE '111400'     TO WS-I18-BE00
           DIVIDE WS-IBAN-18-NUM BY 97
               GIVING WS-QUOT-18 REMAINDER WS-IBAN-REM
           COMPUTE WS-IBAN-CHK = 98 - WS-IBAN-REM

           STRING 'BE'         DELIMITED BY SIZE
                  WS-IBAN-CHK  DELIMITED BY SIZE
                  WS-BANK-CODE DELIMITED BY SIZE
                  WS-SEQ-NUM   DELIMITED BY SIZE
                  WS-NAT-CHK   DELIMITED BY SIZE
             INTO IBAN-VALUE
           END-STRING.

      *================================================================*
      * PROGRAM  : AUDITLOG                                            *
      * PURPOSE  : REGULATORY COMPLIANCE AUDIT LOGGING SUBPROGRAM      *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. AUDITLOG IS INITIAL.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT AUD-FILE ASSIGN TO EXTERNAL DD_AUDFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-AUD-FS.

       DATA DIVISION.
       FILE SECTION.
       FD  AUD-FILE.
       01  AUD-FILE-REC.
           05  AR-REQ-ID               PIC X(12).
           05  AR-SEP1                 PIC X(01).
           05  AR-OPERATION            PIC X(10).
           05  AR-SEP2                 PIC X(01).
           05  AR-CHANNEL              PIC X(08).
           05  AR-SEP3                 PIC X(01).
           05  AR-CUSTOMER-ID          PIC X(10).
           05  AR-SEP4                 PIC X(01).
           05  AR-REQUESTER-ID         PIC X(10).
           05  AR-SEP5                 PIC X(01).
           05  AR-MANDATE              PIC X(12).
           05  AR-SEP6                 PIC X(01).
           05  AR-RISK-STATUS          PIC X(14).
           05  AR-SEP7                 PIC X(01).
           05  AR-RETURN-CODE          PIC X(02).
           05  AR-SEP8                 PIC X(01).
           05  AR-REASON-CODE          PIC X(04).
           05  AR-SEP9                 PIC X(01).
           05  AR-IBAN                 PIC X(16).

       WORKING-STORAGE SECTION.
       01  WS-AUD-FS                   PIC X(02) VALUE SPACES.
       01  WS-ENV-AUD                  PIC X(256) VALUE SPACES.

       LINKAGE SECTION.
       COPY DBAREA.

       PROCEDURE DIVISION USING AUDIT-LOG-AREA.
       0000-MAIN.
           MOVE 'AUD-FAIL' TO AUD-WRITE-STATUS

           ACCEPT WS-ENV-AUD FROM ENVIRONMENT 'DD_AUDFILE'
           IF WS-ENV-AUD = SPACES
               GOBACK
           END-IF

           OPEN EXTEND AUD-FILE
           IF WS-AUD-FS = '35'
               OPEN OUTPUT AUD-FILE
           END-IF
           IF WS-AUD-FS NOT = '00'
               GOBACK
           END-IF

           MOVE SPACES           TO AUD-FILE-REC
           MOVE AUD-REQ-ID       TO AR-REQ-ID
           MOVE '|'              TO AR-SEP1
           MOVE AUD-OPERATION    TO AR-OPERATION
           MOVE '|'              TO AR-SEP2
           MOVE AUD-CHANNEL      TO AR-CHANNEL
           MOVE '|'              TO AR-SEP3
           MOVE AUD-CUSTOMER-ID  TO AR-CUSTOMER-ID
           MOVE '|'              TO AR-SEP4
           MOVE AUD-REQUESTER-ID TO AR-REQUESTER-ID
           MOVE '|'              TO AR-SEP5
           IF AUD-MANDATE = SPACES
               MOVE 'NOT-CHECKED ' TO AR-MANDATE
           ELSE
               MOVE AUD-MANDATE    TO AR-MANDATE
           END-IF
           MOVE '|'              TO AR-SEP6
           IF AUD-RISK-STATUS = SPACES
               MOVE 'NOT-SCREENED  ' TO AR-RISK-STATUS
           ELSE
               MOVE AUD-RISK-STATUS  TO AR-RISK-STATUS
           END-IF
           MOVE '|'              TO AR-SEP7
           MOVE AUD-RETURN-CODE  TO AR-RETURN-CODE
           MOVE '|'              TO AR-SEP8
           MOVE AUD-REASON-CODE  TO AR-REASON-CODE
           MOVE '|'              TO AR-SEP9
           MOVE AUD-ALLOCATED-IBAN TO AR-IBAN

           WRITE AUD-FILE-REC
           IF WS-AUD-FS = '00'
               MOVE 'RECORDED' TO AUD-WRITE-STATUS
           END-IF

           CLOSE AUD-FILE
           GOBACK.

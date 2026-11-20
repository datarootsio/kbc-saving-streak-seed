      *================================================================*
      * PROGRAM  : EXTRISK                                             *
      * PURPOSE  : EXTERNAL CUSTOMER KYC, PEP & SANCTIONS SCREENING    *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. EXTRISK IS INITIAL.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT RISK-FILE ASSIGN TO EXTERNAL DD_RISKFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-RSK-FS.

       DATA DIVISION.
       FILE SECTION.
       FD  RISK-FILE.
       01  RISK-RECORD.
           05  RSK-REC-CUSTOMER-ID     PIC X(10).
           05  RSK-REC-SEP1            PIC X(01).
           05  RSK-REC-KYC-STATUS      PIC X(14).
           05  RSK-REC-SEP2            PIC X(01).
           05  RSK-REC-SEGMENT         PIC X(10).

       WORKING-STORAGE SECTION.
       01  WS-RSK-FS                   PIC X(02) VALUE SPACES.
       01  WS-ENV-PATH                 PIC X(256) VALUE SPACES.
       01  WS-EOF-FLAG                 PIC X(01) VALUE 'N'.
           88  WS-EOF                  VALUE 'Y'.
       01  WS-FOUND-FLAG               PIC X(01) VALUE 'N'.
           88  WS-FOUND                VALUE 'Y'.

       LINKAGE SECTION.
       COPY EXTAREA.

       PROCEDURE DIVISION USING EXT-RISK-AREA.
       0000-MAIN.
           MOVE SPACES TO RISK-OUTPUT

           ACCEPT WS-ENV-PATH FROM ENVIRONMENT 'DD_RISKFILE'
           IF WS-ENV-PATH = SPACES
               MOVE 'SVC-UNAVAIL   ' TO RISK-STATUS
               MOVE 'UNKNOWN   '     TO RISK-SEGMENT
               MOVE 'KYC RISK SCREENING GATEWAY UNAVAILABLE'
                 TO RISK-MESSAGE
               GOBACK
           END-IF

           OPEN INPUT RISK-FILE
           IF WS-RSK-FS NOT = '00'
               MOVE 'SVC-UNAVAIL   ' TO RISK-STATUS
               MOVE 'UNKNOWN   '     TO RISK-SEGMENT
               MOVE 'KYC RISK SCREENING GATEWAY UNAVAILABLE'
                 TO RISK-MESSAGE
               GOBACK
           END-IF

           PERFORM UNTIL WS-EOF OR WS-FOUND
               READ RISK-FILE
                   AT END
                       SET WS-EOF TO TRUE
                   NOT AT END
                       IF RSK-REC-CUSTOMER-ID = RISK-CUSTOMER-ID
                           MOVE RSK-REC-KYC-STATUS TO RISK-STATUS
                           MOVE RSK-REC-SEGMENT    TO RISK-SEGMENT
                           SET WS-FOUND TO TRUE
                       END-IF
               END-READ
           END-PERFORM

           CLOSE RISK-FILE

           IF NOT WS-FOUND
               MOVE 'PEP-HOLD      ' TO RISK-STATUS
               MOVE 'UNVERIFIED'     TO RISK-SEGMENT
               MOVE 'CUSTOMER NOT REGISTERED IN KYC DIRECTORY'
                 TO RISK-MESSAGE
               GOBACK
           END-IF

           EVALUATE TRUE
               WHEN RISK-CLEAR
                   MOVE 'KYC AND SANCTIONS SCREENING CLEAR'
                     TO RISK-MESSAGE
               WHEN RISK-PEP-HOLD
                   MOVE 'CUSTOMER FLAGGED FOR PEP COMPLIANCE REVIEW'
                     TO RISK-MESSAGE
               WHEN RISK-SANCTION-BLOCK
                   MOVE 'CUSTOMER MATCHED ON SANCTIONS WATCHLIST'
                     TO RISK-MESSAGE
               WHEN RISK-SVC-UNAVAIL
                   MOVE 'UPSTREAM SCREENING TIMEOUT FOR CUSTOMER'
                     TO RISK-MESSAGE
               WHEN OTHER
                   MOVE 'SVC-UNAVAIL   ' TO RISK-STATUS
                   MOVE 'UNRECOGNIZED SCREENING STATUS'
                     TO RISK-MESSAGE
           END-EVALUATE

           GOBACK.

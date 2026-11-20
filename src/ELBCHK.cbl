      *================================================================*
      * PROGRAM  : ELBCHK                                              *
      * PURPOSE  : KBC ELB CUSTOMER MANDATE & CHANNEL AUTHORIZATION    *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. ELBCHK IS INITIAL.

       ENVIRONMENT DIVISION.
       INPUT-OUTPUT SECTION.
       FILE-CONTROL.
           SELECT ELB-FILE ASSIGN TO EXTERNAL DD_ELBFILE
               ORGANIZATION IS LINE SEQUENTIAL
               FILE STATUS IS WS-ELB-FS.

       DATA DIVISION.
       FILE SECTION.
       FD  ELB-FILE.
       01  ELB-RECORD.
           05  ELB-REC-CUSTOMER-ID     PIC X(10).
           05  ELB-REC-SEP1            PIC X(01).
           05  ELB-REC-REQUESTER-ID    PIC X(10).
           05  ELB-REC-SEP2            PIC X(01).
           05  ELB-REC-MANDATE-TYPE    PIC X(12).

       WORKING-STORAGE SECTION.
       01  WS-ELB-FS                   PIC X(02) VALUE SPACES.
       01  WS-ENV-PATH                 PIC X(256) VALUE SPACES.
       01  WS-EOF-FLAG                 PIC X(01) VALUE 'N'.
           88  WS-EOF                  VALUE 'Y'.
       01  WS-FOUND-FLAG               PIC X(01) VALUE 'N'.
           88  WS-FOUND                VALUE 'Y'.

       LINKAGE SECTION.
       COPY ELBAREA.

       PROCEDURE DIVISION USING ELB-CHECK-AREA.
       0000-MAIN.
           MOVE 'NONE        ' TO ELB-MANDATE-TYPE
           MOVE 'DENY    '     TO ELB-DECISION
           MOVE +100           TO ELB-SQLCODE

           ACCEPT WS-ENV-PATH FROM ENVIRONMENT 'DD_ELBFILE'
           IF WS-ENV-PATH = SPACES
               MOVE 'ERROR   ' TO ELB-DECISION
               MOVE -904       TO ELB-SQLCODE
               GOBACK
           END-IF

           PERFORM 1000-LOOKUP-MANDATE
           IF ELB-ERROR
               GOBACK
           END-IF

           PERFORM 2000-EVALUATE-AUTHORITY
           GOBACK.

       1000-LOOKUP-MANDATE.
           OPEN INPUT ELB-FILE
           IF WS-ELB-FS NOT = '00'
               MOVE 'ERROR   ' TO ELB-DECISION
               MOVE -904       TO ELB-SQLCODE
               EXIT PARAGRAPH
           END-IF

           PERFORM UNTIL WS-EOF OR WS-FOUND
               READ ELB-FILE
                   AT END
                       SET WS-EOF TO TRUE
                   NOT AT END
                       IF ELB-REC-CUSTOMER-ID = ELB-CUSTOMER-ID
                          AND ELB-REC-REQUESTER-ID = ELB-REQUESTER-ID
                           MOVE ELB-REC-MANDATE-TYPE
                             TO ELB-MANDATE-TYPE
                           MOVE 0 TO ELB-SQLCODE
                           SET WS-FOUND TO TRUE
                       END-IF
               END-READ
           END-PERFORM

           CLOSE ELB-FILE.

       2000-EVALUATE-AUTHORITY.
           IF ELB-ACT-INQ
               IF ELB-MND-WRITE-AUTH OR ELB-MND-POA-VIEW
                   MOVE 'ALLOW   ' TO ELB-DECISION
               ELSE
                   MOVE 'DENY    ' TO ELB-DECISION
               END-IF
               EXIT PARAGRAPH
           END-IF

           EVALUATE ELB-CHANNEL
               WHEN 'MOBILE  '
                   IF ELB-MND-WRITE-AUTH
                       MOVE 'ALLOW   ' TO ELB-DECISION
                   ELSE
                       MOVE 'DENY    ' TO ELB-DECISION
                   END-IF
               WHEN 'BATCH   '
                   IF ELB-MND-HOLDER OR ELB-MND-JOINT-FULL
                       MOVE 'ALLOW   ' TO ELB-DECISION
                   ELSE
                       MOVE 'DENY    ' TO ELB-DECISION
                   END-IF
               WHEN 'BRANCH  '
                   IF ELB-MND-NONE OR ELB-MND-REVOKED
                       MOVE 'DENY    ' TO ELB-DECISION
                   ELSE
                       MOVE 'ALLOW   ' TO ELB-DECISION
                   END-IF
               WHEN OTHER
                   MOVE 'DENY    ' TO ELB-DECISION
           END-EVALUATE.

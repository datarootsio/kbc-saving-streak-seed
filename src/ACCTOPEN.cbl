      *================================================================*
      * PROGRAM  : ACCTOPEN                                            *
      * PURPOSE  : KBC RETAIL ACCOUNT OPENING ORCHESTRATION SERVICE    *
      *================================================================*
       IDENTIFICATION DIVISION.
       PROGRAM-ID. ACCTOPEN IS INITIAL.

       DATA DIVISION.
       WORKING-STORAGE SECTION.
       COPY ELBAREA.
       COPY EXTAREA.
       COPY DBAREA.

       LINKAGE SECTION.
       COPY ACCTREQ.
       COPY ACCTRSP.

       PROCEDURE DIVISION USING ACCT-REQUEST ACCT-RESPONSE.
       0000-MAIN.
           PERFORM 1000-INITIALIZE
           PERFORM 2000-VALIDATE-REQUEST

           IF RSP-RC-OK
               PERFORM 3000-CHECK-ELB-MANDATE
           END-IF

           IF RSP-RC-OK
               PERFORM 4000-SCREEN-CUSTOMER-RISK
           END-IF

           IF NOT RSP-RC-REFUSED AND NOT RSP-RC-TECH-ERR
               PERFORM 5000-ALLOCATE-AND-PERSIST
           END-IF

           PERFORM 9000-WRITE-AUDIT-TRAIL
           GOBACK.

       1000-INITIALIZE.
           INITIALIZE ACCT-RESPONSE
           INITIALIZE ELB-CHECK-AREA
           INITIALIZE EXT-RISK-AREA
           INITIALIZE IBAN-GEN-AREA
           INITIALIZE DB-ACCESS-AREA
           INITIALIZE AUDIT-LOG-AREA
           MOVE '00'   TO RSP-RETURN-CODE
           MOVE '0000' TO RSP-REASON-CODE.

       2000-VALIDATE-REQUEST.
           IF REQ-REQUEST-ID = SPACES
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'RQID' TO RSP-REASON-CODE
               MOVE 'MISSING REQUEST IDENTIFIER' TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF NOT REQ-OP-VALID
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'UNOP' TO RSP-REASON-CODE
               MOVE 'INVALID ACCOUNT SERVICE OPERATION'
                 TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF REQ-OP-OPEN-SUB
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'UNOP' TO RSP-REASON-CODE
               MOVE 'LINKED SUB-ACCOUNT OPENING NOT ENABLED'
                 TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF NOT REQ-CH-VALID
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'CHAN' TO RSP-REASON-CODE
               MOVE 'UNSUPPORTED DISTRIBUTION CHANNEL'
                 TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF REQ-CUSTOMER-ID = SPACES
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'CUST' TO RSP-REASON-CODE
               MOVE 'MISSING CUSTOMER IDENTIFIER' TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF REQ-REQUESTER-ID = SPACES
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'RQST' TO RSP-REASON-CODE
               MOVE 'MISSING REQUESTER IDENTIFIER' TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF NOT REQ-TYPE-VALID
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'TYPE' TO RSP-REASON-CODE
               MOVE 'UNSUPPORTED RETAIL ACCOUNT TYPE' TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF NOT REQ-CCY-SUPPORTED
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'CURR' TO RSP-REASON-CODE
               MOVE 'UNSUPPORTED ACCOUNT CURRENCY' TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF REQ-INIT-DEPOSIT < 0
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'MDEP' TO RSP-REASON-CODE
               MOVE 'INITIAL DEPOSIT CANNOT BE NEGATIVE'
                 TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           IF REQ-TYPE-SAVINGS AND REQ-INIT-DEPOSIT < 10.00
               MOVE '08'   TO RSP-RETURN-CODE
               MOVE 'MDEP' TO RSP-REASON-CODE
               MOVE 'SAVINGS OPENING REQUIRES MINIMUM 10.00 DEPOSIT'
                 TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF.

       3000-CHECK-ELB-MANDATE.
           MOVE REQ-CUSTOMER-ID  TO ELB-CUSTOMER-ID
           MOVE REQ-REQUESTER-ID TO ELB-REQUESTER-ID
           MOVE REQ-CHANNEL      TO ELB-CHANNEL
           MOVE REQ-OPERATION    TO ELB-REQ-ACTION

           CALL 'ELBCHK' USING ELB-CHECK-AREA

           EVALUATE TRUE
               WHEN ELB-ALLOW
                   CONTINUE
               WHEN ELB-DENY
                   MOVE '08'   TO RSP-RETURN-CODE
                   MOVE 'ELBD' TO RSP-REASON-CODE
                   MOVE 'ELB MANDATE REFUSED FOR REQUESTER'
                     TO RSP-MESSAGE
               WHEN OTHER
                   MOVE '12'   TO RSP-RETURN-CODE
                   MOVE 'ELBE' TO RSP-REASON-CODE
                   MOVE 'ELB MANDATE SUBSYSTEM UNAVAILABLE'
                     TO RSP-MESSAGE
           END-EVALUATE.

       4000-SCREEN-CUSTOMER-RISK.
           MOVE REQ-CUSTOMER-ID TO RISK-CUSTOMER-ID
           MOVE REQ-CHANNEL     TO RISK-CHANNEL

           CALL 'EXTRISK' USING EXT-RISK-AREA

           EVALUATE TRUE
               WHEN RISK-CLEAR
                   CONTINUE
               WHEN RISK-SANCTION-BLOCK
                   MOVE '08'   TO RSP-RETURN-CODE
                   MOVE 'SANC' TO RSP-REASON-CODE
                   MOVE 'CUSTOMER BLOCKED BY SANCTIONS SCREENING'
                     TO RSP-MESSAGE
               WHEN RISK-PEP-HOLD
                   MOVE '08'   TO RSP-RETURN-CODE
                   MOVE 'PEPH' TO RSP-REASON-CODE
                   MOVE 'CUSTOMER HELD FOR MANUAL PEP CLEARANCE'
                     TO RSP-MESSAGE
               WHEN RISK-SVC-UNAVAIL
                   MOVE '04'   TO RSP-RETURN-CODE
                   MOVE 'RSKU' TO RSP-REASON-CODE
                   MOVE 'KYC SCREENING TIMEOUT - REVIEW PENDING'
                     TO RSP-MESSAGE
               WHEN OTHER
                   MOVE '12'   TO RSP-RETURN-CODE
                   MOVE 'RSKU' TO RSP-REASON-CODE
                   MOVE 'UNEXPECTED RISK SUBSYSTEM RESPONSE'
                     TO RSP-MESSAGE
           END-EVALUATE.

       5000-ALLOCATE-AND-PERSIST.
           MOVE '736' TO IBAN-BANK-CODE
           MOVE 'PRI' TO IBAN-ACCT-CLASS
           CALL 'IBANGEN' USING IBAN-GEN-AREA
           IF NOT IBAN-RC-OK
               MOVE '12'   TO RSP-RETURN-CODE
               MOVE 'IBNE' TO RSP-REASON-CODE
               MOVE 'FAILED TO ALLOCATE BELGIAN KBC IBAN'
                 TO RSP-MESSAGE
               EXIT PARAGRAPH
           END-IF

           MOVE 'INS-ACCT'       TO DB-OPERATION
           MOVE IBAN-VALUE       TO DB-ACCT-IBAN
           MOVE REQ-REQUEST-ID   TO DB-ACCT-REQ-ID
           MOVE REQ-CUSTOMER-ID  TO DB-ACCT-CUSTOMER-ID
           MOVE REQ-ACCOUNT-TYPE TO DB-ACCT-TYPE
           MOVE REQ-CURRENCY     TO DB-ACCT-CURRENCY
           MOVE REQ-INIT-DEPOSIT TO DB-ACCT-BALANCE
           MOVE 'ACTIVE  '       TO DB-ACCT-STATUS
           MOVE REQ-CHANNEL      TO DB-ACCT-CHANNEL

           CALL 'ACCTDB' USING DB-ACCESS-AREA

           EVALUATE TRUE
               WHEN DB-SQL-OK
                   MOVE IBAN-VALUE TO RSP-ALLOCATED-IBAN
                   IF RSP-RC-OK
                       MOVE 'ACCOUNT OPENED SUCCESSFULLY'
                         TO RSP-MESSAGE
                   END-IF
               WHEN DB-SQL-DUP-KEY
                   MOVE '08'   TO RSP-RETURN-CODE
                   MOVE 'DUPK' TO RSP-REASON-CODE
                   MOVE SPACES TO RSP-ALLOCATED-IBAN
                   MOVE 'DUPLICATE REQUEST IDENTIFIER REJECTED'
                     TO RSP-MESSAGE
               WHEN OTHER
                   MOVE '12'   TO RSP-RETURN-CODE
                   MOVE 'DBUN' TO RSP-REASON-CODE
                   MOVE SPACES TO RSP-ALLOCATED-IBAN
                   MOVE 'ACCOUNT LEDGER DATABASE UNAVAILABLE'
                     TO RSP-MESSAGE
           END-EVALUATE.

       9000-WRITE-AUDIT-TRAIL.
           MOVE REQ-REQUEST-ID     TO AUD-REQ-ID
           MOVE REQ-OPERATION      TO AUD-OPERATION
           MOVE REQ-CHANNEL        TO AUD-CHANNEL
           MOVE REQ-CUSTOMER-ID    TO AUD-CUSTOMER-ID
           MOVE REQ-REQUESTER-ID   TO AUD-REQUESTER-ID
           MOVE ELB-MANDATE-TYPE   TO AUD-MANDATE
           MOVE RISK-STATUS        TO AUD-RISK-STATUS
           MOVE RSP-RETURN-CODE    TO AUD-RETURN-CODE
           MOVE RSP-REASON-CODE    TO AUD-REASON-CODE
           MOVE RSP-ALLOCATED-IBAN TO AUD-ALLOCATED-IBAN

           CALL 'AUDITLOG' USING AUDIT-LOG-AREA
           MOVE AUD-WRITE-STATUS   TO RSP-AUDIT-STATUS.

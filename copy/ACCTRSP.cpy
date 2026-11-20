      *================================================================*
      * COPYBOOK : ACCTRSP                                             *
      * PURPOSE  : KBC RETAIL ACCOUNT OPENING RESPONSE INTERFACE       *
      *================================================================*
       01  ACCT-RESPONSE.
           05  RSP-RETURN-CODE         PIC X(02).
               88  RSP-RC-OK           VALUE '00'.
               88  RSP-RC-WARN         VALUE '04'.
               88  RSP-RC-REFUSED      VALUE '08'.
               88  RSP-RC-TECH-ERR     VALUE '12'.
           05  RSP-REASON-CODE         PIC X(04).
           05  RSP-ALLOCATED-IBAN      PIC X(16).
           05  RSP-PARENT-IBAN         PIC X(16).
           05  RSP-AUDIT-STATUS        PIC X(08).
           05  RSP-MESSAGE             PIC X(60).

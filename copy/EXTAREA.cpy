      *================================================================*
      * COPYBOOK : EXTAREA                                             *
      * PURPOSE  : EXTERNAL KYC/RISK SCREENING & IBAN GENERATOR AREAS  *
      *================================================================*
       01  EXT-RISK-AREA.
           05  RISK-INPUT.
               10  RISK-CUSTOMER-ID    PIC X(10).
               10  RISK-CHANNEL        PIC X(08).
           05  RISK-OUTPUT.
               10  RISK-STATUS         PIC X(14).
                   88  RISK-CLEAR          VALUE 'CLEAR         '.
                   88  RISK-PEP-HOLD       VALUE 'PEP-HOLD      '.
                   88  RISK-SANCTION-BLOCK VALUE 'SANCTION-BLOCK'.
                   88  RISK-SVC-UNAVAIL    VALUE 'SVC-UNAVAIL   '.
               10  RISK-SEGMENT        PIC X(10).
               10  RISK-MESSAGE        PIC X(48).

       01  IBAN-GEN-AREA.
           05  IBAN-INPUT.
               10  IBAN-BANK-CODE      PIC X(03).
               10  IBAN-ACCT-CLASS     PIC X(03).
                   88  IBAN-CLS-PRI    VALUE 'PRI'.
                   88  IBAN-CLS-SUB    VALUE 'SUB'.
           05  IBAN-OUTPUT.
               10  IBAN-RETURN-CODE    PIC X(02).
                   88  IBAN-RC-OK      VALUE '00'.
                   88  IBAN-RC-ERR     VALUE '12'.
               10  IBAN-VALUE          PIC X(16).

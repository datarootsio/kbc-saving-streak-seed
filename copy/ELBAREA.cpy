      *================================================================*
      * COPYBOOK : ELBAREA                                             *
      * PURPOSE  : KBC ELB MANDATE & PERMISSION CHECK COMMAREA         *
      *================================================================*
       01  ELB-CHECK-AREA.
           05  ELB-INPUT.
               10  ELB-CUSTOMER-ID     PIC X(10).
               10  ELB-REQUESTER-ID    PIC X(10).
               10  ELB-CHANNEL         PIC X(08).
               10  ELB-REQ-ACTION      PIC X(10).
                   88  ELB-ACT-OPEN    VALUE 'OPEN-ACCT '.
                   88  ELB-ACT-SUB     VALUE 'OPEN-SUB  '.
                   88  ELB-ACT-INQ     VALUE 'INQUIRE   '.
           05  ELB-OUTPUT.
               10  ELB-MANDATE-TYPE    PIC X(12).
                   88  ELB-MND-HOLDER       VALUE 'HOLDER      '.
                   88  ELB-MND-JOINT-FULL   VALUE 'JOINT-FULL  '.
                   88  ELB-MND-POA-TRANSACT VALUE 'POA-TRANSACT'.
                   88  ELB-MND-POA-VIEW     VALUE 'POA-VIEW    '.
                   88  ELB-MND-REVOKED      VALUE 'REVOKED     '.
                   88  ELB-MND-NONE         VALUE 'NONE        '.
                   88  ELB-MND-WRITE-AUTH   VALUE 'HOLDER      '
                                                  'JOINT-FULL  '
                                                  'POA-TRANSACT'.
               10  ELB-DECISION        PIC X(08).
                   88  ELB-ALLOW       VALUE 'ALLOW   '.
                   88  ELB-DENY        VALUE 'DENY    '.
                   88  ELB-ERROR       VALUE 'ERROR   '.
               10  ELB-SQLCODE         PIC S9(04) COMP.

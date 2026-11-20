      *================================================================*
      * COPYBOOK : ACCTREQ                                             *
      * PURPOSE  : KBC RETAIL ACCOUNT OPENING REQUEST INTERFACE        *
      *================================================================*
       01  ACCT-REQUEST.
           05  REQ-REQUEST-ID          PIC X(12).
           05  REQ-OPERATION           PIC X(10).
               88  REQ-OP-OPEN-ACCT    VALUE 'OPEN-ACCT '.
               88  REQ-OP-OPEN-SUB     VALUE 'OPEN-SUB  '.
               88  REQ-OP-VALID        VALUE 'OPEN-ACCT '
                                             'OPEN-SUB  '.
           05  REQ-CHANNEL             PIC X(08).
               88  REQ-CH-BRANCH       VALUE 'BRANCH  '.
               88  REQ-CH-MOBILE       VALUE 'MOBILE  '.
               88  REQ-CH-BATCH        VALUE 'BATCH   '.
               88  REQ-CH-VALID        VALUE 'BRANCH  '
                                             'MOBILE  '
                                             'BATCH   '.
           05  REQ-CUSTOMER-ID         PIC X(10).
           05  REQ-REQUESTER-ID        PIC X(10).
           05  REQ-ACCOUNT-TYPE        PIC X(08).
               88  REQ-TYPE-CURRENT    VALUE 'CURRENT '.
               88  REQ-TYPE-SAVINGS    VALUE 'SAVINGS '.
               88  REQ-TYPE-VALID      VALUE 'CURRENT '
                                             'SAVINGS '.
           05  REQ-CURRENCY            PIC X(03).
               88  REQ-CCY-EUR         VALUE 'EUR'.
               88  REQ-CCY-USD         VALUE 'USD'.
               88  REQ-CCY-GBP         VALUE 'GBP'.
               88  REQ-CCY-SUPPORTED   VALUE 'EUR' 'USD' 'GBP'.
           05  REQ-INIT-DEPOSIT        PIC S9(09)V99.
           05  REQ-PARENT-IBAN         PIC X(16).
           05  REQ-SUB-PURPOSE         PIC X(12).

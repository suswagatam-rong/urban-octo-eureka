CREATE SEQUENCE ckyc_enq_req_seq START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE;

CREATE TABLE ckyc_enquiry_attempts (
  request_id            NUMBER(19) PRIMARY KEY,
    cif                   VARCHAR2(32) NOT NULL,
      branch_code           VARCHAR2(20),
        cif_status            VARCHAR2(20),
          id_type               VARCHAR2(4),
            id_number             VARCHAR2(165),
              attempt_no            NUMBER(5),
                txn_id                VARCHAR2(64),
                  request_payload       BLOB,
                    response_payload      BLOB,
                      ckyc_no               VARCHAR2(16),
                        ckyc_reference_id     VARCHAR2(64),
                          name                  VARCHAR2(400),
                            fathers_name          VARCHAR2(400),
                              age                   NUMBER(3),
                                image_type            VARCHAR2(16),
                                  photo_base64          CLOB,
                                    kyc_date              DATE,
                                      updated_date          DATE,
                                        response_code         VARCHAR2(64),
                                          response_remarks      VARCHAR2(4000),
                                            response_xml          CLOB,
                                              request_envelope      CLOB,
                                                response_envelope     CLOB,
                                                  request_created_at    TIMESTAMP DEFAULT SYSTIMESTAMP,
                                                    response_received_at  TIMESTAMP,
                                                      status_flag           VARCHAR2(1) DEFAULT 'N'
                                                      );
                                                      CREATE INDEX idx_ckyc_attempts_cif ON ckyc_enquiry_attempts (cif);
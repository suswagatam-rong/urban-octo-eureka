package com.example.ckyc.dto;

import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlRootElement;

@XmlRootElement(name="PID_DATA")
public class CersaiEnquiryResponse {
    private String ckycNo;
        private String ckycReferenceId;
            private String name;
                private String fathersName;
                    private Integer age;
                        private String imageType;
                            private String photoBase64;
                                private String kycDate;
                                    private String updatedDate;
                                        private String responseCode;
                                            private String remarks;

                                                @XmlElement(name="CKYC_NO") public String getCkycNo(){ return ckycNo; } public void setCkycNo(String s){ this.ckycNo=s; }
                                                    @XmlElement(name="CKYC_REFERENCE_ID") public String getCkycReferenceId(){ return ckycReferenceId; } public void setCkycReferenceId(String s){ this.ckycReferenceId=s; }
                                                        @XmlElement(name="NAME") public String getName(){ return name; } public void setName(String s){ this.name=s; }
                                                            @XmlElement(name="FATHERS_NAME") public String getFathersName(){ return fathersName; } public void setFathersName(String s){ this.fathersName=s; }
                                                                @XmlElement(name="AGE") public Integer getAge(){ return age; } public void setAge(Integer a){ this.age=a; }
                                                                    @XmlElement(name="IMAGE_TYPE") public String getImageType(){ return imageType; } public void setImageType(String s){ this.imageType=s; }
                                                                        @XmlElement(name="PHOTO") public String getPhotoBase64(){ return photoBase64; } public void setPhotoBase64(String s){ this.photoBase64=s; }
                                                                            @XmlElement(name="KYC_DATE") public String getKycDate(){ return kycDate; } public void setKycDate(String s){ this.kycDate=s; }
                                                                                @XmlElement(name="UPDATED_DATE") public String getUpdatedDate(){ return updatedDate; } public void setUpdatedDate(String s){ this.updatedDate=s; }
                                                                                    @XmlElement(name="RESPONSE_CODE") public String getResponseCode(){ return responseCode; } public void setResponseCode(String s){ this.responseCode=s; }
                                                                                        @XmlElement(name="REMARKS") public String getRemarks(){ return remarks; } public void setRemarks(String s){ this.remarks=s; }
                                                                                        }
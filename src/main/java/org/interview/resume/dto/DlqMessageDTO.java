package org.interview.resume.dto;

public class DlqMessageDTO {
    private String recordId;
    private Long resumeId;
    private String errorTime;

    public DlqMessageDTO() {}

    public DlqMessageDTO(String recordId, Long resumeId, String errorTime) {
        this.recordId = recordId;
        this.resumeId = resumeId;
        this.errorTime = errorTime;
    }

    public String getRecordId() { return recordId; }
    public void setRecordId(String recordId) { this.recordId = recordId; }
    public Long getResumeId() { return resumeId; }
    public void setResumeId(Long resumeId) { this.resumeId = resumeId; }
    public String getErrorTime() { return errorTime; }
    public void setErrorTime(String errorTime) { this.errorTime = errorTime; }
}

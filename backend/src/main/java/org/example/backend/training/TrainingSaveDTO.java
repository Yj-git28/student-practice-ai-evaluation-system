package org.example.backend.training;

/** 实训新增/编辑入参：时间用字符串接收，格式 yyyy-MM-dd HH:mm:ss，允许为空 */
public class TrainingSaveDTO {

    /** 发布范围：1=教师专属 / 2=年级公共 / 3=全校公共 */
    private Integer scopeType;
    /** scope_type=2 时的年级值，如 "2024级"；其他情况忽略 */
    private String scopeValue;

    private String trainingName;
    private String description;
    private String startTime;
    private String endTime;

    public String getTrainingName() { return trainingName; }
    public void setTrainingName(String trainingName) { this.trainingName = trainingName; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStartTime() { return startTime; }
    public void setStartTime(String startTime) { this.startTime = startTime; }

    public String getEndTime() { return endTime; }
    public void setEndTime(String endTime) { this.endTime = endTime; }

    public Integer getScopeType() { return scopeType; }
    public void setScopeType(Integer scopeType) { this.scopeType = scopeType; }

    public String getScopeValue() { return scopeValue; }
    public void setScopeValue(String scopeValue) { this.scopeValue = scopeValue; }
}

package org.example.backend.system.dto;

/** 班级新增/编辑入参 */
public class ClassSaveDTO {

    private String className;
    private String grade;

    public String getClassName() { return className; }
    public void setClassName(String className) { this.className = className; }

    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
}

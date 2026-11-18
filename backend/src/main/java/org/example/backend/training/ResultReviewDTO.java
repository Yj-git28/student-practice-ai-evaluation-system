package org.example.backend.training;

/** 教师人工复核入参 */
public class ResultReviewDTO {

    private Integer finalScore;
    private String teacherComment;

    public Integer getFinalScore() { return finalScore; }
    public void setFinalScore(Integer finalScore) { this.finalScore = finalScore; }

    public String getTeacherComment() { return teacherComment; }
    public void setTeacherComment(String teacherComment) { this.teacherComment = teacherComment; }
}

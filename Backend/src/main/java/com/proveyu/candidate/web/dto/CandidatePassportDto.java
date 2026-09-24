package com.proveyu.candidate.web.dto;

import java.util.List;
import java.util.UUID;

public class CandidatePassportDto {

    public static class SkillScoreSummary {
        private UUID skillId;
        private String skillName;
        private double averagePercentage;
        private int testsTaken;

        public SkillScoreSummary() {}

        public SkillScoreSummary(UUID skillId, String skillName, double averagePercentage, int testsTaken) {
            this.skillId = skillId;
            this.skillName = skillName;
            this.averagePercentage = averagePercentage;
            this.testsTaken = testsTaken;
        }

        public UUID getSkillId() { return skillId; }
        public String getSkillName() { return skillName; }
        public double getAveragePercentage() { return averagePercentage; }
        public int getTestsTaken() { return testsTaken; }
    }

    public static class ExamScoreSummary {
        private UUID scoreId;
        private UUID examId;
        private String examTitle;
        private double score;
        private double maxScore;
        private double percentage;
        private boolean passed;

        public ExamScoreSummary() {}

        public ExamScoreSummary(UUID scoreId, UUID examId, String examTitle, double score, double maxScore, double percentage, boolean passed) {
            this.scoreId = scoreId;
            this.examId = examId;
            this.examTitle = examTitle;
            this.score = score;
            this.maxScore = maxScore;
            this.percentage = percentage;
            this.passed = passed;
        }

        public UUID getScoreId() { return scoreId; }
        public UUID getExamId() { return examId; }
        public String getExamTitle() { return examTitle; }
        public double getScore() { return score; }
        public double getMaxScore() { return maxScore; }
        public double getPercentage() { return percentage; }
        public boolean isPassed() { return passed; }
    }

    public static class PassportResponse {
        private UUID candidateId;
        private String fullName;
        private String email;
        private String experienceTrack;
        private double overallAverageScore;
        private String overallStatus;
        private List<SkillScoreSummary> skillScores;
        private List<ExamScoreSummary> examHistory;

        public PassportResponse() {}

        public PassportResponse(UUID candidateId, String fullName, String email, String experienceTrack, double overallAverageScore, String overallStatus, List<SkillScoreSummary> skillScores, List<ExamScoreSummary> examHistory) {
            this.candidateId = candidateId;
            this.fullName = fullName;
            this.email = email;
            this.experienceTrack = experienceTrack;
            this.overallAverageScore = overallAverageScore;
            this.overallStatus = overallStatus;
            this.skillScores = skillScores;
            this.examHistory = examHistory;
        }

        public UUID getCandidateId() { return candidateId; }
        public String getFullName() { return fullName; }
        public String getEmail() { return email; }
        public String getExperienceTrack() { return experienceTrack; }
        public double getOverallAverageScore() { return overallAverageScore; }
        public String getOverallStatus() { return overallStatus; }
        public List<SkillScoreSummary> getSkillScores() { return skillScores; }
        public List<ExamScoreSummary> getExamHistory() { return examHistory; }
    }
}

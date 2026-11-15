package com.proveyu.college.domain;

public final class CollegeEnums {
    private CollegeEnums() {}

    public enum ApprovalStatus { PENDING, APPROVED, REJECTED }
    public enum PolicyMode { OPEN, ONE_OFFER, TIER_UPGRADE }
    public enum MemberRole { COLLEGE_ADMIN, COLLEGE_COORDINATOR, COLLEGE_VIEWER }
    public enum MemberStatus { ACTIVE, REVOKED }
    public enum StudentStatus { ACTIVE, PLACED, DEBARRED, OPTED_OUT }
    public enum DocType { RESUME, MARKSHEET_10, MARKSHEET_12, MARKSHEET_SEM, ID_PROOF, OTHER }
    public enum VerificationStatus { PENDING, VERIFIED, REJECTED }
    public enum MouStatus { NONE, IN_PROGRESS, SIGNED, EXPIRED }
    public enum DriveType { ON_CAMPUS, OFF_CAMPUS, POOL, INTERNSHIP, PPO }
    public enum DriveMode { OFFLINE, ONLINE, HYBRID }
    public enum DriveStatus { DRAFT, PUBLISHED, ONGOING, COMPLETED, CANCELLED }
    public enum OverrideAction { INCLUDE, EXCLUDE }
    public enum ApplicationStatus { APPLIED, SHORTLISTED, WAITLISTED, IN_PROCESS, SELECTED, REJECTED, WITHDRAWN }
    public enum ApplicationSource { SELF, TPO, OVERRIDE }
    public enum RoundType { APTITUDE, CODING, GD, TECHNICAL, HR, CUSTOM }
    public enum RoundStatus { PENDING, ONGOING, COMPLETED }
    public enum RoundResultValue { PENDING, PASS, FAIL, ABSENT, HOLD }
    public enum SlotStatus { SCHEDULED, COMPLETED, CANCELLED, NO_SHOW }
    public enum OfferStatus { OFFERED, ACCEPTED, DECLINED, JOINED, REVOKED }
    public enum ReportFormat { XLSX, PDF, CSV }
    public enum ReportFrequency { DAILY, WEEKLY, MONTHLY }
}

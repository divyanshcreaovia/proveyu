/* TypeScript mirrors of the backend DTOs (com.proveyu.college.application.dto.* and auth DTOs). Keep in sync. */

export type Role = 'CANDIDATE' | 'RECRUITER' | 'ORGANIZATION' | 'ADMIN' | 'COLLEGE_ADMIN' | 'COLLEGE_COORDINATOR' | 'COLLEGE_VIEWER';
export type MemberRole = 'COLLEGE_ADMIN' | 'COLLEGE_COORDINATOR' | 'COLLEGE_VIEWER';
export type MemberStatus = 'ACTIVE' | 'REVOKED';
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'SUSPENDED';
export type PolicyMode = 'OPEN' | 'ONE_OFFER' | 'TIER_UPGRADE';
export type StudentStatus = 'ACTIVE' | 'PLACED' | 'DEBARRED' | 'OPTED_OUT';
export type DocType = 'RESUME' | 'MARKSHEET_10' | 'MARKSHEET_12' | 'MARKSHEET_SEM' | 'ID_PROOF' | 'OTHER'
  | 'PHOTO' | 'CERTIFICATE' | 'INTERNSHIP_CERTIFICATE' | 'COVER_LETTER' | 'OFFER_LETTER';
export const DOC_TYPE_LABELS: Record<DocType, string> = {
  RESUME: 'Resume / CV', MARKSHEET_10: '10th marksheet', MARKSHEET_12: '12th / diploma marksheet', MARKSHEET_SEM: 'Semester marksheet',
  ID_PROOF: 'ID proof', PHOTO: 'Passport photo', CERTIFICATE: 'Certificate', INTERNSHIP_CERTIFICATE: 'Internship certificate',
  COVER_LETTER: 'Cover letter', OFFER_LETTER: 'Offer letter', OTHER: 'Other',
};
export type VerificationStatus = 'PENDING' | 'VERIFIED' | 'REJECTED';
export type MouStatus = 'NONE' | 'IN_PROGRESS' | 'SIGNED' | 'EXPIRED';
export type DriveType = 'ON_CAMPUS' | 'OFF_CAMPUS' | 'POOL' | 'INTERNSHIP' | 'PPO';
export type DriveMode = 'OFFLINE' | 'ONLINE' | 'HYBRID';
export type DriveStatus = 'DRAFT' | 'PUBLISHED' | 'ONGOING' | 'COMPLETED' | 'CANCELLED';
export type OverrideAction = 'INCLUDE' | 'EXCLUDE';
export type ApplicationStatus = 'APPLIED' | 'SHORTLISTED' | 'WAITLISTED' | 'IN_PROCESS' | 'SELECTED' | 'REJECTED' | 'WITHDRAWN';
export type ApplicationSource = 'SELF' | 'TPO' | 'OVERRIDE';
export type RoundType = 'APTITUDE' | 'CODING' | 'GD' | 'TECHNICAL' | 'HR' | 'CUSTOM';
export type RoundStatus = 'PENDING' | 'ONGOING' | 'COMPLETED';
export type RoundResultValue = 'PENDING' | 'PASS' | 'FAIL' | 'ABSENT' | 'HOLD';
export type SlotStatus = 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
export type OfferStatus = 'OFFERED' | 'ACCEPTED' | 'DECLINED' | 'JOINED' | 'REVOKED';
export type ReportFormat = 'XLSX' | 'PDF' | 'CSV';
export type ReportFrequency = 'DAILY' | 'WEEKLY' | 'MONTHLY';

// ---------------------------------------------------------------- auth
export interface AuthSession {
  token: string;
  userId: string;
  email: string;
  fullName: string;
  role: Role;
  emailVerified?: boolean;
  collegeId?: string;
  collegeName?: string;
  collegeApprovalStatus?: ApprovalStatus;
}

export interface CollegeRegisterRequest {
  collegeName: string; aicteCode: string; university: string; city: string; state: string; emailDomain: string; website?: string;
  tpoName: string; tpoPhone: string; tpoEmail: string; password: string; acceptTerms: boolean;
}
export interface RegisterResponse { collegeId: string; email: string; approvalStatus: ApprovalStatus; message: string; devVerificationToken?: string; }
export interface InviteInfo { collegeName: string; email: string; fullName: string; role: MemberRole; expiresAt: string; }
export interface ForgotPasswordResponse { email: string; devOtp?: string; message: string; }

export interface Me {
  userId: string; fullName: string; email: string; role: MemberRole; designation?: string; branchScope: string[];
  collegeId: string; collegeName: string; approvalStatus: ApprovalStatus; approvalNote?: string; hasLogo: boolean; brandColor?: string;
}

// ---------------------------------------------------------------- settings
export interface CollegeProfile {
  collegeId: string; collegeName: string; aicteCode: string; university?: string; city?: string; state?: string; emailDomain?: string;
  website?: string; tpoName?: string; tpoEmail?: string; tpoPhone?: string; approvalStatus: ApprovalStatus; currentAcademicYear?: string;
  hasLogo: boolean; brandColor?: string;
}
export interface Policy { policyMode: PolicyMode; maxApplications: number | null; maxOffers: number; upgradeMinHikePercent: number; blockAfterAccept: boolean; }
export interface Branch { id: string; code: string; name: string; students: number; }
export interface Batch { id: string; gradYear: number; label: string; active: boolean; students: number; }
export interface Tier { id?: string | null; name: string; tierRank: number; minCtcLpa: number; }
export interface EmailTemplate { key: string; subject: string; body: string; customized: boolean; }

// ---------------------------------------------------------------- students
export interface Student {
  id: string; rollNumber: string; firstName: string; lastName?: string; fullName: string; email: string; phone?: string; gender?: string;
  dob?: string; branchId?: string; branchName?: string; branchCode?: string; batchYear: number; degree?: string; cgpa?: number;
  tenthPercent?: number; twelfthPercent?: number; activeBacklogs: number; gapYears: number; skills: string[]; linkedinUrl?: string;
  githubUrl?: string; status: StudentStatus; highestCtcLpa?: number; highestTierRank?: number; hasAccount: boolean; deleted: boolean;
  createdAt: string; updatedAt: string;
}
export interface StudentRequest {
  rollNumber: string; firstName: string; lastName?: string | null; email: string; phone?: string | null; gender?: string | null; dob?: string | null;
  branchId: string; batchYear: number; degree?: string | null; cgpa?: number | null; tenthPercent?: number | null; twelfthPercent?: number | null;
  activeBacklogs?: number | null; gapYears?: number | null; skills?: string[]; linkedinUrl?: string | null; githubUrl?: string | null;
}
export interface StudentFilter {
  q?: string; batchYear?: number | null; branchId?: string | null; minCgpa?: number | null; maxCgpa?: number | null; maxBacklogs?: number | null;
  status?: StudentStatus | null; placed?: boolean | null; skill?: string; deleted?: boolean;
}
export interface StudentDocument {
  id: string; docType: DocType; label?: string; fileId: string; fileName: string; contentType: string; sizeBytes: number;
  verificationStatus: VerificationStatus; remarks?: string; verifiedAt?: string; createdAt: string;
}
export interface SkillScore { skill: string; score: number; maxScore: number; passed: boolean; }
export interface Passport {
  verifiedScores: SkillScore[]; averagePercent?: number; resumeUrl?: string; headline?: string; candidateSkills: string[];
  profileCompleteness: number; missingProfileItems: string[];
}
export interface StudentDetail {
  student: Student; documents: StudentDocument[];
  applications: { applicationId: string; driveId: string; driveTitle: string; companyName: string; status: ApplicationStatus; currentRound: number; appliedAt: string }[];
  offers: { offerId: string; companyName: string; roleTitle: string; ctcLpa: number; status: OfferStatus; offerDate?: string }[];
  passport: Passport;
}
export interface RowIssue { row: number; field: string; message: string; }
export interface ImportResult {
  jobId: string; dryRun: boolean; totalRows: number; validRows: number; created: number; updated: number; errorRows: number; duplicateRows: number;
  errors: RowIssue[]; duplicates: RowIssue[];
  preview: { row: number; action: 'CREATE' | 'UPDATE'; rollNumber: string; name: string; email: string; branchCode: string; batchYear: number; cgpa?: number }[];
}
export interface ImportHistory {
  id: string; fileName: string; dryRun: boolean; totalRows: number; createdRows: number; updatedRows: number; errorRows: number; duplicateRows: number; createdAt: string;
}

// ---------------------------------------------------------------- companies
export interface Company {
  id: string; name: string; industry?: string; website?: string; hqLocation?: string; mouStatus: MouStatus; mouExpiry?: string;
  linkedRecruiterId?: string; linkedRecruiterName?: string; notes?: string; drivesCount: number; offersCount: number; avgRating?: number;
  primaryContactName?: string; primaryContactEmail?: string; deleted: boolean; createdAt: string;
}
export interface CompanyRequest {
  name: string; industry?: string | null; website?: string | null; hqLocation?: string | null; mouStatus?: MouStatus | null; mouExpiry?: string | null;
  linkedRecruiterId?: string | null; notes?: string | null;
}
export interface Contact { id: string; name: string; designation?: string; email?: string; phone?: string; primaryContact: boolean; }
export interface Feedback { id: string; driveId?: string; driveTitle?: string; rating: number; comment?: string; createdByName: string; createdAt: string; }
export interface CompanyDetail {
  company: Company; contacts: Contact[]; feedback: Feedback[];
  visits: { driveId: string; title: string; roleTitle: string; status: DriveStatus; driveDate?: string; ctcLpa?: number; applicants: number; offers: number }[];
}
export interface RecruiterOption { userId: string; fullName: string; email: string; }

// ---------------------------------------------------------------- drives
export interface Eligibility {
  minCgpa?: number | null; minTenth?: number | null; minTwelfth?: number | null; maxActiveBacklogs?: number | null; maxGapYears?: number | null;
  batchYears?: number[]; branchIds?: string[]; requiredSkills?: string[];
}
export interface DriveRequest {
  companyId: string; title: string; roleTitle: string; driveType: DriveType; ctcLpa?: number | null; stipendMonthly?: number | null;
  ctcBreakup?: string | null; location?: string | null; bondMonths?: number | null; bondDetails?: string | null; jobDescription?: string | null;
  skills?: string[]; mode?: DriveMode; venue?: string | null; registrationDeadline?: string | null; driveDate?: string | null; tierId?: string | null;
  eligibility?: Eligibility;
}
export interface Drive extends Omit<DriveRequest, 'eligibility'> {
  id: string; companyName?: string; tierName?: string; tierRank?: number; status: DriveStatus; eligibility: Eligibility; publishedAt?: string;
  deleted: boolean; createdAt: string; applicationCounts: Record<string, number>; roundsCount: number; offersCount: number; bondMonths: number;
  skills: string[];
}
export interface EligibilityRow {
  studentId: string; rollNumber: string; name: string; branchCode?: string; batchYear: number; cgpa?: number; activeBacklogs: number;
  studentStatus: StudentStatus; eligible: boolean; overridden: boolean; reasons: string[]; applicationStatus?: ApplicationStatus;
}
export interface EligibilityPreview {
  totalStudents: number; eligible: number; ineligible: number; applied: number; page: number; size: number; totalElements: number;
  totalPages: number; content: EligibilityRow[];
}
export interface Override { id: string; studentId: string; rollNumber: string; studentName: string; action: OverrideAction; reason: string; createdAt: string; }
export interface Application {
  id: string; driveId: string; studentId: string; rollNumber: string; studentName: string; email: string; branchCode?: string; batchYear: number;
  cgpa?: number; status: ApplicationStatus; source: ApplicationSource; currentRound: number; remarks?: string; appliedAt: string;
}
export interface BulkResult { updated: number; skipped: string[]; }
export interface Round {
  id: string; driveId: string; seq: number; name: string; roundType: RoundType; scheduledAt?: string; status: RoundStatus;
  resultCounts: Record<string, number>;
}
export interface RoundResultRow {
  resultId: string; applicationId: string; studentId: string; rollNumber: string; studentName: string; branchCode?: string;
  result: RoundResultValue; score?: number | null; remarks?: string | null;
}
export interface Slot {
  id: string; driveId: string; driveTitle?: string; companyName?: string; roundId?: string; roundName?: string; applicationId: string;
  studentId?: string; studentName?: string; rollNumber?: string; panelName?: string; panelMembers?: string; startsAt: string; endsAt: string;
  room?: string; meetingLink?: string; status: SlotStatus; rescheduleCount: number; reminderSentAt?: string;
}
export interface SlotRequest {
  driveId: string; roundId?: string | null; applicationId: string; panelName?: string | null; panelMembers?: string | null;
  startsAt: string; endsAt: string; room?: string | null; meetingLink?: string | null;
}
export interface Conflict { type: 'STUDENT' | 'PANEL' | 'ROOM'; message: string; slotId: string; }
export interface Offer {
  id: string; studentId: string; rollNumber: string; studentName: string; branchCode?: string; batchYear?: number; driveId?: string; driveTitle?: string;
  companyId: string; companyName?: string; roleTitle: string; ctcLpa: number; ctcBreakup?: string; tierRank?: number; tierName?: string;
  offerDate?: string; joiningDate?: string; status: OfferStatus; offerLetterFileId?: string; remarks?: string; createdAt: string;
}
export interface OfferRequest {
  studentId: string; driveId?: string | null; companyId: string; roleTitle: string; ctcLpa: number; ctcBreakup?: string | null;
  offerDate?: string | null; joiningDate?: string | null; remarks?: string | null; overridePolicy: boolean; overrideReason?: string | null;
}

// ---------------------------------------------------------------- team
export interface Member {
  id: string; userId: string; fullName: string; email: string; phone?: string; role: MemberRole; designation?: string; branchScope: string[];
  branchNames: string[]; status: MemberStatus; lastLoginAt?: string; createdAt: string;
}
export interface Invite {
  id: string; email: string; fullName: string; role: MemberRole; designation?: string; branchScope: string[]; expiresAt: string; createdAt: string;
  devInviteToken?: string;
}
export interface RoleMatrixRow { permission: string; admin: boolean; coordinator: boolean; viewer: boolean; }

// ---------------------------------------------------------------- analytics & reports
export interface Kpis {
  totalStudents: number; eligibleStudents: number; registeredStudents: number; placedStudents: number; placementPercent: number;
  avgCtc?: number; medianCtc?: number; highestCtc?: number; activeDrives: number; upcomingInterviews: number; pendingOffers: number; totalOffers: number;
}
export interface Bucket { label: string; count: number; }
export interface Dashboard {
  kpis: Kpis; branchWise: { branch: string; code: string; total: number; placed: number; percent: number }[]; ctcDistribution: Bucket[];
  companyWise: Bucket[]; monthlyTrend: { month: string; offers: number; placed: number }[]; funnel: { stage: string; count: number }[];
  tierWise: Bucket[]; batches: number[];
}
export interface SkillGapRow { skill: string; demand: number; supply: number; avgVerifiedScore?: number; gapIndex: number; }
export interface ReportType { key: string; description: string; }
export interface ReportTable { title: string; headers: string[]; rows: (string | number)[][]; }
export interface ReportSchedule {
  id: string; reportType: string; format: ReportFormat; frequency: ReportFrequency; recipients: string; active: boolean; lastRunAt?: string; nextRunAt: string;
}

// ---------------------------------------------------------------- communication / audit / search
export interface AudienceFilter { batchYears?: number[]; branchIds?: string[]; statuses?: StudentStatus[]; placed?: boolean | null; driveId?: string | null; }
export interface Announcement {
  id: string; title: string; body: string; filter?: AudienceFilter; recipientCount: number; sendEmail: boolean; createdByName: string; createdAt: string;
  readCount?: number;
}
export interface ChatContact { userId: string; name: string; type: 'TEAM' | 'RECRUITER' | 'STUDENT'; subtitle?: string; }
export interface AuditEntry {
  id: string; actorUserId?: string; actorName?: string; action: string; entityType: string; entityId?: string; summary: string; details?: string; createdAt: string;
}
export interface SearchHit { id: string; type: 'student' | 'company' | 'drive'; title: string; subtitle: string; }
export interface SearchResult { students: SearchHit[]; companies: SearchHit[]; drives: SearchHit[]; }
export interface AppNotification { id: string; type: string; message: string; read: boolean; createdAt: string; }

// ---------------------------------------------------------------- student portal
export interface PortalProfile {
  studentId: string; userId: string; collegeName: string; rollNumber: string; name: string; email: string; phone?: string; branch?: string;
  batchYear: number; degree?: string; cgpa?: number; tenthPercent?: number; twelfthPercent?: number; activeBacklogs: number; skills: string[];
  linkedinUrl?: string; githubUrl?: string; status: StudentStatus; highestCtcLpa?: number; placementPolicy: PolicyMode; unreadAnnouncements: number;
}
export interface PortalProfileUpdate { phone?: string; skills: string[]; linkedinUrl?: string; githubUrl?: string; }
export interface PortalRound {
  roundId: string; seq: number; name: string; roundType: string; status: 'PENDING' | 'ONGOING' | 'COMPLETED'; scheduledAt?: string;
  result?: 'PENDING' | 'PASS' | 'FAIL' | 'ABSENT' | 'HOLD';
}
export interface PortalInterview {
  id: string; applicationId: string; companyName: string; driveTitle: string; roundName?: string; startsAt: string; endsAt: string;
  room?: string; meetingLink?: string; panelName?: string; status: 'SCHEDULED' | 'COMPLETED' | 'CANCELLED' | 'NO_SHOW';
}
export interface PortalAnnouncement { id: string; title: string; body: string; fromName: string; createdAt: string; read: boolean; }
export interface PortalContact { userId: string; name: string; type: 'TPO' | 'COORDINATOR'; subtitle?: string; }
export interface PortalDrive {
  id: string; title: string; companyName: string; roleTitle: string; driveType: DriveType; ctcLpa?: number; stipendMonthly?: number; location?: string;
  mode: DriveMode; venue?: string; registrationDeadline?: string; driveDate?: string; status: DriveStatus; jobDescription?: string; skills: string[];
  bondMonths: number; tierName?: string; eligible: boolean; reasons: string[]; applicationId?: string; applicationStatus?: ApplicationStatus; canApply: boolean;
}
export interface PortalApplication { id: string; driveId: string; driveTitle: string; companyName: string; status: ApplicationStatus; currentRound: number; appliedAt: string; }

// ---------------------------------------------------------------- admin
export interface CollegeSummary {
  collegeId: string; collegeName: string; aicteCode: string; university?: string; city?: string; state?: string; emailDomain?: string;
  tpoName?: string; tpoEmail?: string; tpoPhone?: string; approvalStatus: ApprovalStatus; approvalNote?: string; createdAt: string;
}

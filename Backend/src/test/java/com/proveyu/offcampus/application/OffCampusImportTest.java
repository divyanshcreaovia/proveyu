package com.proveyu.offcampus.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.offcampus.application.OffCampusImportService.Imported;
import com.proveyu.offcampus.domain.OffCampusModel.JobField;
import com.proveyu.offcampus.domain.OffCampusModel.JobType;
import com.proveyu.offcampus.domain.OffCampusModel.Source;
import com.proveyu.offcampus.domain.OffCampusModel.WorkMode;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OffCampusImportTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Instant NOW = Instant.parse("2026-11-19T00:00:00Z");

    private static JsonNode job(String title, String date, String extra) throws IOException {
        return JSON.readTree("""
                {"title": "%s", "company": "Acme <b>Labs</b>", "locations": "Pune, Maharashtra", "date": "%s",
                 "description": "We hire <b>freshers</b> &amp; interns.", "url": "https://jobviewtrack.com/en-in/job-1234/" %s}"""
                .formatted(title, date, extra));
    }

    @Test
    void freshJobWithYearlySalaryBecomesADraft() throws IOException {
        Imported j = OffCampusImportService.mapCareerjet(job("Junior Data Analyst", "Tue, 17 Nov 2026 10:00:00 GMT",
                ", \"salary_min\": 350000, \"salary_max\": 500000, \"salary_type\": \"Y\", \"salary_currency_code\": \"INR\""),
                JobField.SOFTWARE, false, NOW, 15);
        assertThat(j).isNotNull();
        assertThat(j.source()).isEqualTo(Source.CAREERJET);
        assertThat(j.sourceId()).startsWith("cj-").hasSize(35);
        assertThat(j.company()).isEqualTo("Acme Labs");
        assertThat(j.location()).isEqualTo("Pune, Maharashtra");
        assertThat(j.jobType()).isEqualTo(JobType.FULL_TIME);
        assertThat(j.field()).isEqualTo(JobField.DATA); // the title wins over the search it came from
        assertThat(j.ctcMin()).isEqualByComparingTo(new BigDecimal("3.5"));
        assertThat(j.ctcMax()).isEqualByComparingTo(new BigDecimal("5"));
        assertThat(j.description()).startsWith("We hire freshers & interns.").contains("Careerjet");
        assertThat(j.postedAt()).isEqualTo(Instant.parse("2026-11-17T10:00:00Z"));
    }

    @Test
    void internshipSearchGivesAMonthlyStipendAndTheSearchedField() throws IOException {
        Imported j = OffCampusImportService.mapCareerjet(job("Graduate Trainee (Work from home)", "2026-11-18T08:00:00Z",
                ", \"salary_min\": 10000, \"salary_type\": \"M\", \"salary_currency_code\": \"INR\""), JobField.MECHANICAL, true, NOW, 15);
        assertThat(j.jobType()).isEqualTo(JobType.INTERNSHIP);
        assertThat(j.workMode()).isEqualTo(WorkMode.REMOTE);
        assertThat(j.field()).isEqualTo(JobField.MECHANICAL); // title says nothing, so the search's field is used
        assertThat(j.stipend()).isEqualTo(10_000);
        assertThat(j.ctcMin()).isNull();
    }

    @Test
    void sameAdGetsTheSameIdAndForeignSalaryIsIgnored() throws IOException {
        Imported a = OffCampusImportService.mapCareerjet(job("Civil Site Engineer", "2026-11-18", ", \"salary_min\": 40000, \"salary_currency_code\": \"USD\""),
                JobField.CIVIL, false, NOW, 15);
        Imported b = OffCampusImportService.mapCareerjet(job("Civil Site Engineer", "2026-11-18", ""), JobField.SOFTWARE, false, NOW, 15);
        assertThat(a.sourceId()).isEqualTo(b.sourceId());
        assertThat(a.ctcMin()).isNull();
        assertThat(a.field()).isEqualTo(JobField.CIVIL);
    }

    @Test
    void seniorRolesAndExperiencedAdsAreSkippedAndInternshipNeedsTheTitle() throws IOException {
        assertThat(OffCampusImportService.notForFreshers("Software Development Engineer III", "")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("Senior Genesys Routing Developer", "")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("Sr. Data Analyst", "")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("Engineering Manager", "")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("SDE-2 Backend", "")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("Java Developer", "Needs 3+ years of experience in Spring.")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("Java Developer", "4-6 yrs experience")).isTrue();
        assertThat(OffCampusImportService.notForFreshers("Java Developer", "0-1 years experience, freshers welcome")).isFalse();
        assertThat(OffCampusImportService.notForFreshers("Graduate Engineer Trainee", "1 year training bond")).isFalse();
        assertThat(OffCampusImportService.notForFreshers("Software Engineer", "")).isFalse();
        assertThat(OffCampusImportService.mapCareerjet(job("Senior Data Analyst", "2026-11-18", ""), JobField.DATA, false, NOW, 15)).isNull();
        Imported fromInternSearch = OffCampusImportService.mapCareerjet(job("Software Engineer", "2026-11-18", ""), JobField.SOFTWARE, true, NOW, 15);
        assertThat(fromInternSearch.jobType()).isEqualTo(JobType.FULL_TIME); // the internship search returned a regular job
        assertThat(OffCampusImportService.mapCareerjet(job("Full Stack Developer Intern", "2026-11-18", ""), JobField.SOFTWARE, true, NOW, 15).jobType())
                .isEqualTo(JobType.INTERNSHIP);
    }

    @Test
    void oldOrIncompleteJobsAreSkipped() throws IOException {
        assertThat(OffCampusImportService.mapCareerjet(job("Software Engineer", "2026-10-01", ""), JobField.SOFTWARE, false, NOW, 15)).isNull();
        assertThat(OffCampusImportService.mapCareerjet(JSON.readTree("{\"title\": \"X\", \"url\": \"https://a.b\", \"date\": \"2026-11-18\"}"),
                JobField.SOFTWARE, false, NOW, 15)).isNull(); // no company
        assertThat(OffCampusImportService.mapCareerjet(JSON.readTree("{\"title\": \"X\", \"company\": \"Y\", \"url\": \"javascript:x\"}"),
                JobField.SOFTWARE, false, NOW, 15)).isNull();
    }

    @Test
    void urlCarriesTheSearchAndTheUser() {
        OffCampusImportService s = new OffCampusImportService(null, JSON, 15, 0, "key", "https://search.api.careerjet.net/v4/query/", "en_IN",
                "", "Mozilla/5.0 test", 2, "https://proveyu.example");
        assertThat(s.configured()).isTrue();
        String fresher = s.careerjetUrl(JobField.DATA, false, 1, "1.2.3.4");
        assertThat(fresher).startsWith("https://search.api.careerjet.net/v4/query?locale_code=en_IN&keywords=data+analyst+fresher")
                .contains("sort=date").contains("page_size=100").contains("page=1").contains("user_ip=1.2.3.4").contains("user_agent=Mozilla%2F5.0+test")
                .doesNotContain("contract_type");
        assertThat(s.careerjetUrl(JobField.MECHANICAL, true, 2, "1.2.3.4")).contains("keywords=mechanical+engineer&").contains("page=2")
                .endsWith("&contract_type=i");
        assertThat(new OffCampusImportService(null, JSON, 15, 0, " ", "u", "en_IN", "", "a", 2, "").configured()).isFalse();
    }

    @Test
    void titlesAreSortedIntoFields() {
        assertThat(JobField.classify("Data Science Intern")).isEqualTo(JobField.DATA);
        assertThat(JobField.classify("Java Full Stack Developer")).isEqualTo(JobField.SOFTWARE);
        assertThat(JobField.classify("Graduate Engineer Trainee - Mechanical")).isEqualTo(JobField.MECHANICAL);
        assertThat(JobField.classify("Embedded Systems Engineer")).isEqualTo(JobField.ELECTRICAL);
        assertThat(JobField.classify("UI/UX Designer")).isEqualTo(JobField.DESIGN);
        assertThat(JobField.classify("Manual QA Tester")).isEqualTo(JobField.TESTING);
        assertThat(JobField.classify("Accounts Executive")).isEqualTo(JobField.FINANCE);
        assertThat(JobField.classify("HR Recruiter")).isEqualTo(JobField.HR);
        assertThat(JobField.classify("Academic Counsellor")).isEqualTo(JobField.OTHER);
    }

    @Test
    void datesInSeveralFormatsAreRead() {
        assertThat(OffCampusImportService.parseDate("Tue, 18 Nov 2025 10:00:00 GMT")).isEqualTo(Instant.parse("2025-11-18T10:00:00Z"));
        assertThat(OffCampusImportService.parseDate("2025-11-18T10:00:00+05:30")).isEqualTo(Instant.parse("2025-11-18T04:30:00Z"));
        assertThat(OffCampusImportService.parseDate("2025-11-18")).isEqualTo(Instant.parse("2025-11-18T00:00:00Z"));
        assertThat(OffCampusImportService.parseDate("Mon, 18 Nov 2025 10:00:00 GMT")).isEqualTo(Instant.parse("2025-11-18T10:00:00Z")); // wrong weekday
        assertThat(OffCampusImportService.parseDate("yesterday")).isNull();
    }

    @Test
    void nocLetterIsAPdfAndHandlesNonLatinText() throws IOException {
        byte[] pdf = NocLetter.pdf(new NocLetter.Data("NOC/2026/ABCD1234", java.time.LocalDate.of(2026, 11, 19), "Demo Institute", "Pune, Maharashtra",
                null, "Anaïs Śharma", "CS-21-001", "B.Tech", "Computer Science", 2027, "Acme Labs", "Software Engineer", "Full-time", "8.5 LPA",
                "Joining on 1 July – after exams", "Dr. R. Rao", "tpo@demo.edu"));
        assertThat(new String(pdf, 0, 5)).isEqualTo("%PDF-");
        assertThat(NocLetter.safe("Śharma – ₹8")).isEqualTo("Sharma - Rs. 8");
    }
}

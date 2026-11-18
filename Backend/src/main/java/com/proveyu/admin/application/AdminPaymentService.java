package com.proveyu.admin.application;

import com.proveyu.college.application.TabularExporter;
import com.proveyu.college.application.TabularExporter.Export;
import com.proveyu.college.application.TabularExporter.Table;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** All AI interview payments (every status, every date) as an Excel / CSV file, for accounts. */
@Service
@RequiredArgsConstructor
public class AdminPaymentService {

    private static final Set<String> STATUSES = Set.of("PENDING", "SUBMITTED", "PAID", "REJECTED");

    private final JdbcTemplate jdbc;
    private final TabularExporter exporter;

    public Export export(String status, String format) {
        String st = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (st != null && !STATUSES.contains(st)) throw new DomainException("Unknown status", HttpStatus.BAD_REQUEST, "BAD_STATUS");
        String fmt = format == null || format.isBlank() ? "xlsx" : format.trim().toLowerCase(Locale.ROOT);
        List<Object> args = new ArrayList<>();
        String where = "";
        if (st != null) { where = " where p.status = ?"; args.add(st); }
        List<List<Object>> rows = jdbc.query("""
                select p.reference, p.status, concat(coalesce(s.first_name, ''), ' ', coalesce(s.last_name, '')), s.roll_number, u.email,
                       c.college_name, p.quantity, p.amount_paise / 100, p.utr, p.created_at, p.submitted_at, p.reviewed_at, p.review_note
                from interview_purchases p
                left join college_students s on s.id = p.student_id
                left join users u on u.id = p.user_id
                left join college_profiles c on c.college_id = p.college_id""" + where + " order by p.created_at desc",
                (rs, i) -> {
                    List<Object> r = new ArrayList<>();
                    for (int c = 1; c <= 13; c++) r.add(rs.getObject(c));
                    r.set(2, String.valueOf(r.get(2)).trim());
                    return r;
                }, args.toArray());
        Table t = new Table("AI interview payments" + (st == null ? "" : " (" + st.toLowerCase(Locale.ROOT) + ")"),
                List.of("Reference", "Status", "Student", "Roll number", "E-mail", "College", "Interviews", "Amount (Rs)", "UTR", "Created",
                        "Submitted", "Reviewed", "Note"), rows);
        return exporter.export(t, fmt, "interview-payments");
    }
}

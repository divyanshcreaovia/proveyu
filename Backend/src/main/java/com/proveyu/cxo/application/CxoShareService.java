package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proveyu.cxo.application.SpecSanitizer.CollegeRef;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Insight;
import com.proveyu.cxo.domain.CxoModel.ShareLink;
import com.proveyu.cxo.domain.CxoModel.SharedBoard;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.security.CxoContext;
import com.proveyu.shared.error.DomainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only links to a saved dashboard. The link carries a random secret (256 bits); only its SHA-256 is stored. Opening a link runs
 * the dashboard's widgets for the dashboard's own college, so the numbers are fresh, and leaves out any list of student names.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CxoShareService {

    private static final int MAX_LINKS_PER_DASHBOARD = 10;

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SpecSanitizer sanitizer;
    private final QueryEngine engine;
    private final InsightWriter insights;
    private final MetricCatalog catalog;
    private final SecureRandom random = new SecureRandom();

    public ShareLink create(CxoContext ctx, UUID dashboardId, Integer days) {
        int n = days == null ? 30 : Math.max(1, Math.min(days, 180));
        Integer own = jdbc.queryForObject("select count(*) from cxo_dashboards where id = ? and college_id = ? and user_id = ?", Integer.class,
                dashboardId.toString(), ctx.collegeId().toString(), ctx.userId().toString());
        if (own == null || own == 0) throw new DomainException("Dashboard not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
        Integer live = jdbc.queryForObject("select count(*) from cxo_dashboard_shares where dashboard_id = ? and revoked_at is null and expires_at > utc_timestamp()",
                Integer.class, dashboardId.toString());
        if (live != null && live >= MAX_LINKS_PER_DASHBOARD) {
            throw new DomainException("This dashboard already has " + MAX_LINKS_PER_DASHBOARD + " active links. Revoke one first.", HttpStatus.BAD_REQUEST, "LIMIT");
        }
        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        UUID id = UUID.randomUUID();
        LocalDateTime expires = LocalDateTime.now(ZoneOffset.UTC).plusDays(n);
        jdbc.update("insert into cxo_dashboard_shares (id, dashboard_id, college_id, created_by, token_hash, expires_at) values (?,?,?,?,?,?)",
                id.toString(), dashboardId.toString(), ctx.collegeId().toString(), ctx.userId().toString(), hash(token), expires);
        log.info("CXO share link created for dashboard {} (college {}, {} days)", dashboardId, ctx.collegeId(), n);
        return new ShareLink(id, dashboardId, token, "/cxo/share/" + token, expires.toInstant(ZoneOffset.UTC), Instant.now(), 0, false);
    }

    /** The links of one of this CXO's dashboards. The secret is never shown again: only the status. */
    public List<ShareLink> list(CxoContext ctx, UUID dashboardId) {
        return jdbc.query("""
                select s.id, s.dashboard_id, s.expires_at, s.created_at, s.views, s.revoked_at from cxo_dashboard_shares s
                join cxo_dashboards d on d.id = s.dashboard_id and d.college_id = ? and d.user_id = ?
                where s.dashboard_id = ? order by s.created_at desc""", (rs, i) -> new ShareLink(UUID.fromString(rs.getString(1)), UUID.fromString(rs.getString(2)), null, null,
                rs.getTimestamp(3).toInstant(), rs.getTimestamp(4).toInstant(), rs.getInt(5), rs.getTimestamp(6) != null),
                ctx.collegeId().toString(), ctx.userId().toString(), dashboardId.toString());
    }

    public void revoke(CxoContext ctx, UUID shareId) {
        int n = jdbc.update("""
                update cxo_dashboard_shares s join cxo_dashboards d on d.id = s.dashboard_id and d.college_id = ? and d.user_id = ?
                set s.revoked_at = utc_timestamp() where s.id = ? and s.revoked_at is null""", ctx.collegeId().toString(), ctx.userId().toString(), shareId.toString());
        if (n == 0) throw new DomainException("Link not found", HttpStatus.NOT_FOUND, "NOT_FOUND");
    }

    /** The board for an outside reader. Unknown, expired and revoked links all answer the same "not found". */
    public SharedBoard open(String token) {
        if (token == null || token.length() < 20 || token.length() > 100) throw notFound();
        List<Map<String, Object>> rows = jdbc.queryForList("""
                select s.id, s.college_id, d.title, d.prompt, d.spec_json, s.expires_at, u.full_name, cp.college_name
                from cxo_dashboard_shares s join cxo_dashboards d on d.id = s.dashboard_id join users u on u.id = d.user_id
                join college_profiles cp on cp.college_id = s.college_id
                where s.token_hash = ? and s.revoked_at is null and s.expires_at > utc_timestamp() and cp.approval_status <> 'SUSPENDED'""", hash(token));
        if (rows.isEmpty()) throw notFound();
        Map<String, Object> r = rows.get(0);
        UUID collegeId = UUID.fromString((String) r.get("college_id"));
        jdbc.update("update cxo_dashboard_shares set views = views + 1, last_viewed_at = utc_timestamp() where id = ?", r.get("id"));

        DashboardSpec saved;
        try {
            saved = json.readValue((String) r.get("spec_json"), DashboardSpec.class);
        } catch (Exception e) {
            throw notFound();
        }
        CollegeRef ref = ref(collegeId);
        List<WidgetSpec> run = sanitizer.forRun(saved, ref).stream().filter(w -> w.dimension() != Dimension.STUDENT).toList(); // no student names outside
        Map<String, WidgetData> data = new LinkedHashMap<>();
        for (WidgetSpec w : run) data.put(w.id(), safe(w, collegeId));
        DashboardSpec spec = new DashboardSpec((String) r.get("title"), null, run);
        Insight insight = null;
        try {
            insight = insights.write(collegeId, (String) r.get("prompt"), spec, data);
        } catch (Exception e) {
            log.warn("Share insight failed: {}", e.toString());
        }
        return new SharedBoard((String) r.get("title"), (String) r.get("college_name"), (String) r.get("full_name"), spec, List.copyOf(data.values()), insight,
                instant(r.get("expires_at")), Instant.now());
    }

    private WidgetData safe(WidgetSpec w, UUID collegeId) {
        try {
            return engine.run(w, collegeId);
        } catch (Exception e) {
            Unit unit = catalog.find(w.metric()).map(MetricCatalog.Def::unit).orElse(Unit.COUNT);
            return new WidgetData(w.id(), unit, null, List.of(), List.of());
        }
    }

    private CollegeRef ref(UUID collegeId) {
        String c = collegeId.toString();
        List<String> branches = jdbc.queryForList("select code from college_branches where college_id = ? order by code", String.class, c);
        java.util.Set<Integer> batches = new java.util.TreeSet<>(jdbc.queryForList("select grad_year from college_batches where college_id = ?", Integer.class, c));
        batches.addAll(jdbc.queryForList("select distinct batch_year from college_students where college_id = ? and deleted_at is null and batch_year is not null", Integer.class, c));
        return new CollegeRef(branches, new java.util.ArrayList<>(batches));
    }

    private static Instant instant(Object t) {
        return t instanceof java.sql.Timestamp ts ? ts.toInstant() : ((LocalDateTime) t).toInstant(ZoneOffset.UTC);
    }

    private static DomainException notFound() {
        return new DomainException("This link is not valid any more", HttpStatus.NOT_FOUND, "LINK_NOT_FOUND");
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}

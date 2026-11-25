package com.proveyu.cxo.application;

import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Runs widget queries from the {@link MetricCatalog}. Uses its own JdbcTemplate with a query timeout and a row cap, so a dashboard
 * can never hold the database for long or pull a large result. Only SELECTs built by the catalog reach it.
 */
@Slf4j
@Component
public class QueryEngine {

    private final MetricCatalog catalog;
    private final JdbcTemplate jdbc;

    public QueryEngine(MetricCatalog catalog, DataSource dataSource, @Value("${cxo.query.timeout-seconds:8}") int timeoutSeconds) {
        this.catalog = catalog;
        this.jdbc = new JdbcTemplate(dataSource);
        this.jdbc.setQueryTimeout(Math.max(1, timeoutSeconds));
        this.jdbc.setMaxRows(50);
    }

    /** Data for one validated widget, scoped to the given college. */
    public WidgetData run(WidgetSpec w, UUID collegeId) {
        MetricCatalog.Def d = catalog.find(w.metric()).orElseThrow();
        if (catalog.isFunnel(d)) {
            List<String> labels = new ArrayList<>();
            List<Double> values = new ArrayList<>();
            for (Map.Entry<String, MetricCatalog.Query> stage : catalog.funnel(w, collegeId)) {
                labels.add(stage.getKey());
                values.add(single(stage.getValue()));
            }
            return new WidgetData(w.id(), d.unit(), null, labels, values);
        }
        MetricCatalog.Query q = catalog.build(d, w, collegeId);
        if (q.single()) {
            Double value = single(q);
            Comparison c = previous(d, w, collegeId);
            if (c == null) return new WidgetData(w.id(), d.unit(), value, List.of(), List.of());
            return new WidgetData(w.id(), d.unit(), value, List.of(), List.of(), single(catalog.build(d, c.spec(), collegeId)), c.label());
        }
        List<String> labels = new ArrayList<>();
        List<Double> values = new ArrayList<>();
        jdbc.query(q.sql(), rs -> {
            labels.add(rs.getString("label"));
            values.add(number(rs.getObject("v")));
        }, q.args().toArray());
        if (w.dimension() == Dimension.MONTH) return monthly(w, d.unit(), labels, values);
        if (w.dimension() == Dimension.BATCH && d.unit() == Unit.COUNT) return everyBatch(w, collegeId, labels, values);
        return new WidgetData(w.id(), d.unit(), null, labels, values);
    }

    /**
     * A trend shows every month of its range, not only months with data: counts are 0 in a quiet month, averages stay empty (a gap).
     * Range: the widget's dates, else the last 12 months (the same window the query used).
     */
    private static WidgetData monthly(WidgetSpec w, Unit unit, List<String> labels, List<Double> values) {
        YearMonth start = YearMonth.from(w.from() != null ? w.from() : LocalDate.now().withDayOfMonth(1).minusMonths(11));
        YearMonth end = YearMonth.from(w.to() != null ? w.to() : LocalDate.now());
        if (end.isBefore(start) || start.plusMonths(36).isBefore(end)) return new WidgetData(w.id(), unit, null, labels, values);
        Map<String, Double> byMonth = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) byMonth.put(labels.get(i), values.get(i));
        List<String> l = new ArrayList<>();
        List<Double> v = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            String key = m.toString(); // yyyy-MM, as the query labels months
            l.add(key);
            v.add(byMonth.getOrDefault(key, unit == Unit.COUNT ? 0.0 : null));
        }
        return new WidgetData(w.id(), unit, null, l, v);
    }

    /** Counts by batch list every batch of the college, in year order, so a batch with nothing yet shows 0 beside the others. */
    private WidgetData everyBatch(WidgetSpec w, UUID collegeId, List<String> labels, List<Double> values) {
        Map<String, Double> byBatch = new HashMap<>();
        for (int i = 0; i < labels.size(); i++) byBatch.put(labels.get(i), values.get(i));
        java.util.TreeSet<String> all = new java.util.TreeSet<>(labels);
        all.addAll(jdbc.queryForList("select distinct cast(batch_year as char) from college_students where college_id = ? and deleted_at is null"
                + " and batch_year is not null", String.class, collegeId.toString()));
        List<String> l = new ArrayList<>(all);
        List<Double> v = new ArrayList<>();
        for (String b : l) v.add(byBatch.getOrDefault(b, 0.0));
        return new WidgetData(w.id(), Unit.COUNT, null, l, v);
    }

    private record Comparison(WidgetSpec spec, String label) {}

    /**
     * What a KPI is compared with: the previous batch when it is filtered to a batch (and that batch has students), else the same
     * period a year earlier when it has a date range of up to a year. Null when there is nothing fair to compare with.
     */
    private Comparison previous(MetricCatalog.Def d, WidgetSpec w, UUID collegeId) {
        if (w.batchYear() != null && d.batchCol() != null) {
            int prev = w.batchYear() - 1;
            Integer students = jdbc.queryForObject("select count(*) from college_students where college_id = ? and batch_year = ? and deleted_at is null",
                    Integer.class, collegeId.toString(), prev);
            if (students == null || students == 0) return null;
            return new Comparison(new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), prev, w.branchCode(), w.from(), w.to(),
                    w.limit(), w.size()), "batch " + prev);
        }
        if (w.from() != null && d.dateCol() != null) {
            LocalDate end = w.to() != null ? w.to() : LocalDate.now();
            if (end.isBefore(w.from()) || w.from().plusYears(1).isBefore(end)) return null;
            LocalDate from = w.from().minusYears(1), to = end.minusYears(1);
            return new Comparison(new WidgetSpec(w.id(), w.title(), w.type(), w.metric(), w.dimension(), w.batchYear(), w.branchCode(), from, to,
                    w.limit(), w.size()), "same period " + from.getYear());
        }
        return null;
    }

    private Double single(MetricCatalog.Query q) {
        List<Double> r = jdbc.query(q.sql(), (rs, i) -> number(rs.getObject(1)), q.args().toArray());
        return r.isEmpty() ? null : r.get(0);
    }

    private static Double number(Object o) {
        if (o == null) return null;
        if (o instanceof BigDecimal b) return b.setScale(2, RoundingMode.HALF_UP).doubleValue();
        if (o instanceof Number n) return Math.round(n.doubleValue() * 100) / 100.0;
        try {
            return Double.parseDouble(o.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}

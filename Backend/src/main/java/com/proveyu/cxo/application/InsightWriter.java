package com.proveyu.cxo.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proveyu.cxo.domain.CxoModel.DashboardSpec;
import com.proveyu.cxo.domain.CxoModel.Dimension;
import com.proveyu.cxo.domain.CxoModel.Insight;
import com.proveyu.cxo.domain.CxoModel.Unit;
import com.proveyu.cxo.domain.CxoModel.WidgetData;
import com.proveyu.cxo.domain.CxoModel.WidgetSpec;
import com.proveyu.cxo.domain.CxoModel.WidgetType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Writes the short "what this means" note above a dashboard. The model sees only aggregated numbers (counts, averages, labels such as
 * branch codes, months and company names), never student-level data. Without AI a factual note is built from the same numbers.
 */
@Component
@RequiredArgsConstructor
public class InsightWriter {

    private final CxoAiClient ai;
    private final ObjectMapper json;
    private final CxoAiCache cache;

    /** The summary for these numbers: from the cache when the same question gave the same numbers before, else from the model. */
    public Insight write(UUID collegeId, String question, DashboardSpec spec, Map<String, WidgetData> data) {
        ArrayNode facts = json.createArrayNode();
        for (WidgetSpec w : spec.widgets()) {
            WidgetData d = data.get(w.id());
            if (d == null) continue;
            ObjectNode f = facts.addObject().put("widget", w.title()).put("unit", d.unit().name().toLowerCase());
            if (w.type() == WidgetType.KPI) {
                if (d.value() == null) f.putNull("value"); else f.put("value", d.value());
                if (d.previous() != null) f.put("previous_value", d.previous()).put("previous_is", d.previousLabel());
            } else if (w.dimension() == Dimension.STUDENT) {
                // names never leave the server; the row count is how many names the list shows (capped), not a total to quote
                f.put("note", "list of student names shown on screen, not a count; use the KPI widgets for totals")
                        .put("names_shown", d.labels().size());
            } else {
                ArrayNode pts = f.putArray("points");
                for (int i = 0; i < Math.min(15, d.labels().size()); i++) {
                    Double v = d.values().get(i);
                    ObjectNode pt = pts.addObject().put("label", d.labels().get(i));
                    if (v == null) pt.putNull("value"); else pt.put("value", v);
                }
            }
        }
        String key = CxoAiCache.key("insight-v1", CxoAiCache.normalize(question), facts.toString());
        Optional<Insight> hit = cache.get(collegeId, CxoAiCache.Kind.INSIGHT, key).flatMap(this::read);
        if (hit.isPresent()) return hit.get();
        Optional<JsonNode> r = ai.json("""
                You are an analyst briefing a college principal. From the numbers given, write JSON only:
                {"headline": "one sentence, the single most important takeaway, max 22 words",
                 "bullets": ["2 or 3 short, specific observations or actions, each max 20 words, quoting the numbers"]}
                Use only the numbers given; never invent data. If the numbers are all zero or empty, say there is no data yet and what to do.
                Plain business English, Indian context (LPA for salaries), no markdown.""",
                "Question: " + question + "\nNumbers: " + facts, 0.3);
        if (r.isPresent() && r.get().hasNonNull("headline")) {
            List<String> bullets = new ArrayList<>();
            for (JsonNode b : r.get().path("bullets")) if (b.isTextual() && !b.asText().isBlank() && bullets.size() < 3) bullets.add(b.asText().strip());
            Insight insight = new Insight(r.get().path("headline").asText().strip(), bullets);
            try {
                cache.put(collegeId, CxoAiCache.Kind.INSIGHT, key, json.writeValueAsString(insight));
            } catch (Exception ignored) {
                // not cached: the next request asks the model again
            }
            return insight;
        }
        return facts(spec, data);
    }

    private Optional<Insight> read(String s) {
        try {
            return Optional.of(json.readValue(s, Insight.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Factual note without AI: the KPI values and the leader of the first ranked chart. */
    Insight facts(DashboardSpec spec, Map<String, WidgetData> data) {
        List<String> bullets = new ArrayList<>();
        StringBuilder head = new StringBuilder();
        for (WidgetSpec w : spec.widgets()) {
            WidgetData d = data.get(w.id());
            if (d == null) continue;
            if (w.type() == WidgetType.KPI && d.value() != null && head.length() < 120) {
                if (head.length() > 0) head.append(" · ");
                head.append(w.title()).append(": ").append(format(d.value(), d.unit()));
            } else if (w.type() != WidgetType.KPI && !d.labels().isEmpty() && bullets.size() < 3
                    && w.dimension() != null && w.dimension().name().matches("BRANCH|COMPANY|TEST|TIER")
                    && d.values().get(0) != null && d.values().get(0) > 0) { // nothing "leads" with zero
                bullets.add(w.title() + ": " + d.labels().get(0) + " leads with " + format(d.values().get(0), d.unit()) + ".");
            }
        }
        boolean empty = data.values().stream().allMatch(d -> (d.value() == null || d.value() == 0)
                && d.values().stream().allMatch(v -> v == null || v == 0));
        if (empty) return new Insight("There is no data for this view yet.", List.of("Numbers appear here as soon as students, drives and offers are recorded."));
        return new Insight(head.length() == 0 ? spec.title() : head.toString(), bullets);
    }

    static String format(Double v, Unit unit) {
        if (v == null) return "-";
        String n = v == Math.rint(v) ? String.valueOf(v.longValue()) : String.format("%.1f", v);
        return switch (unit) {
            case PERCENT -> n + "%";
            case LPA -> n + " LPA";
            case CGPA -> n + " CGPA";
            default -> n;
        };
    }
}

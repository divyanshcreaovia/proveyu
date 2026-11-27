package com.proveyu.cxo.application;

import java.util.List;
import java.util.Objects;

/** Small number helpers shared by the CXO views. */
final class CxoMath {
    private CxoMath() {}

    /** The middle value (mean of the two middle values for an even count); null for no values. */
    static Double median(List<Double> values) {
        List<Double> v = values.stream().filter(Objects::nonNull).sorted().toList();
        if (v.isEmpty()) return null;
        int n = v.size();
        double m = n % 2 == 1 ? v.get(n / 2) : (v.get(n / 2 - 1) + v.get(n / 2)) / 2.0;
        return Math.round(m * 100) / 100.0;
    }

    static Double mean(List<Double> values) {
        List<Double> v = values.stream().filter(Objects::nonNull).toList();
        return v.isEmpty() ? null : Math.round(v.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100) / 100.0;
    }
}

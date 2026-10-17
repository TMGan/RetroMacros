package com.mealplanner.service;

import com.mealplanner.model.BodyPart;
import com.mealplanner.model.MeasurementEntry;
import com.mealplanner.model.User;
import com.mealplanner.repository.MeasurementEntryRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/** Monthly tape measurements: saving, history, and the numbers the progress page shows. */
@Service
public class MeasurementService {

    /** Clients are nudged to measure again once this many days have passed. */
    public static final int REMIND_AFTER_DAYS = 28;

    private final MeasurementEntryRepository repository;
    private final FoodLogService foodLogService;

    public MeasurementService(MeasurementEntryRepository repository, FoodLogService foodLogService) {
        this.repository = repository;
        this.foodLogService = foodLogService;
    }

    /** Saves the given spots for that day. Spots left out keep their earlier value from the same day, if any. */
    public MeasurementEntry save(User user, LocalDate date, Map<BodyPart, Double> values) {
        MeasurementEntry entry = repository.findByUserAndMeasuredOn(user, date).orElseGet(MeasurementEntry::new);
        entry.setUser(user);
        entry.setMeasuredOn(date);
        values.forEach((part, value) -> part.set(entry, value));
        return repository.save(entry);
    }

    public List<MeasurementEntry> history(User user) {
        return repository.findByUserOrderByMeasuredOnAsc(user);
    }

    /** True when the client has never measured, or the last session is 4 weeks old. */
    public boolean isDue(User user) {
        return repository.findTopByUserOrderByMeasuredOnDesc(user)
                .map(e -> !e.getMeasuredOn().isAfter(foodLogService.today(user).minusDays(REMIND_AFTER_DAYS)))
                .orElse(true);
    }

    public Map<String, Object> summary(User user) {
        List<MeasurementEntry> sessions = history(user);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("sessionCount", sessions.size());
        s.put("hasData", !sessions.isEmpty());
        s.put("due", isDue(user));
        s.put("firstDate", sessions.isEmpty() ? null : sessions.get(0).getMeasuredOn());
        s.put("lastDate", sessions.isEmpty() ? null : sessions.get(sessions.size() - 1).getMeasuredOn());
        s.put("nextDue", sessions.isEmpty() ? null : sessions.get(sessions.size() - 1).getMeasuredOn().plusDays(REMIND_AFTER_DAYS));

        List<Map<String, Object>> rows = new ArrayList<>();
        double total = 0;
        boolean hasTotal = false;
        for (BodyPart part : BodyPart.values()) {
            List<Double> values = sessions.stream().map(part::get).filter(Objects::nonNull).toList();
            if (values.isEmpty()) continue;
            double first = values.get(0);
            double latest = values.get(values.size() - 1);
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", part.getKey());
            row.put("label", part.getLabel());
            row.put("first", first);
            row.put("latest", latest);
            row.put("previous", values.size() >= 2 ? values.get(values.size() - 2) : null);
            row.put("sinceLast", values.size() >= 2 ? round1(latest - values.get(values.size() - 2)) : null);
            row.put("total", values.size() >= 2 ? round1(latest - first) : null);
            rows.add(row);
            if (values.size() >= 2) {
                total += latest - first;
                hasTotal = true;
            }
        }
        s.put("rows", rows);
        // Net change across every spot measured at least twice. Negative means inches lost.
        s.put("totalChange", hasTotal ? round1(total) : null);
        s.put("totalLost", hasTotal ? round1(-total) : null);
        s.put("totalAbs", hasTotal ? round1(Math.abs(total)) : null);
        s.put("waistChange", rows.stream().filter(r -> "waist".equals(r.get("key"))).map(r -> r.get("total")).findFirst().orElse(null));
        return s;
    }

    /** Dates plus one numeric series per spot, for the measurement chart. Numbers and keys only. */
    public Map<String, Object> chartData(User user) {
        List<MeasurementEntry> sessions = history(user);
        Map<String, Object> series = new LinkedHashMap<>();
        Map<String, String> labels = new LinkedHashMap<>();
        for (BodyPart part : BodyPart.values()) {
            List<Double> values = sessions.stream().map(part::get).toList();
            if (values.stream().anyMatch(Objects::nonNull)) {
                series.put(part.getKey(), values);
                labels.put(part.getKey(), part.getLabel());
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("dates", sessions.stream().map(e -> e.getMeasuredOn().toString()).toList());
        data.put("series", series);
        data.put("labels", labels);
        return data;
    }

    /** The diagram rows: each spot with its last value, used as a placeholder for the next session. */
    public List<Map<String, Object>> diagram(User user) {
        Map<BodyPart, Double> last = new EnumMap<>(BodyPart.class);
        for (MeasurementEntry e : history(user)) {
            for (BodyPart part : BodyPart.values()) {
                if (part.get(e) != null) last.put(part, part.get(e));
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (BodyPart part : BodyPart.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("part", part);
            row.put("last", last.get(part));
            rows.add(row);
        }
        return rows;
    }

    private static Double round1(double value) {
        return Math.round(value * 10) / 10.0;
    }
}

package thienloc.manage.config;

import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import thienloc.manage.entity.EffIncentiveRate;
import thienloc.manage.entity.EffMultiplier;
import thienloc.manage.repository.EffIncentiveRateRepository;
import thienloc.manage.repository.EffMultiplierRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Order(2)
public class EffDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EffDataInitializer.class);

    private final EffMultiplierRepository multiplierRepo;
    private final EffIncentiveRateRepository rateRepo;

    @Override
    public void run(ApplicationArguments args) {
        seedMultipliers();
        seedRates();
    }

    private void seedMultipliers() {
        // Upsert: update existing rows to keep values in sync with source data
        upsertMultiplier("SEW8",   "SEW",  8,  null,   1.0, 1.3333, 2.9333, 3.1733, 4.72, 5.0362, 4.956, 5.192, 6.136);
        upsertMultiplier("SEW10",  "SEW",  10, null,   1.0, 1.3333, 2.9333, 3.1733, 4.72, 5.0362, 4.956, 5.192, 6.136);
        upsertMultiplier("SEW4",   "SEW",  4,  null,   1.0, 1.3333, 2.9333, 3.1733, 4.72, 5.0362, 4.956, 5.192, 6.136);
        upsertMultiplier("ASSY8",  "ASSY", 8,  null,   1.0, 1.20, 1.50, 2.025, 2.73375, 2.93625, 3.34125, 3.645, 3.94875);
        upsertMultiplier("ASSY10", "ASSY", 10, null,   1.0, 1.20, 1.50, 2.025, 2.73375, 2.93625, 3.34125, 3.645, 3.94875);
        upsertMultiplier("ASSY4",  "ASSY", 4,  null,   1.0, 1.20, 1.50, 2.025, 2.73375, 2.93625, 3.34125, 3.645, 3.94875);
        upsertMultiplier("SF8",    "SF",   8,  null,   1.0, 1.10, 1.20, 1.44, 1.50, 1.56, 1.62, 1.74, 1.86);
        upsertMultiplier("SF10",   "SF",   10, null,   1.0, 1.10, 1.20, 1.44, 1.50, 1.56, 1.62, 1.74, 1.86);
        upsertMultiplier("SF4",    "SF",   4,  null,   1.0, 1.10, 1.20, 1.44, 1.50, 1.56, 1.62, 1.74, 1.86);
        upsertMultiplier("BUFF8",  "BUFF", 8,  "SF8",  1.0, 1.10, 1.20, 1.44, 1.50, 1.56, 1.62, 1.74, 1.86);
        upsertMultiplier("BUFF10", "BUFF", 10, "SF10", 1.0, 1.10, 1.20, 1.44, 1.50, 1.56, 1.62, 1.74, 1.86);
        upsertMultiplier("BUFF4",  "BUFF", 4,  "SF4",  1.0, 1.10, 1.20, 1.44, 1.50, 1.56, 1.62, 1.74, 1.86);
        log.info("EffMultiplier: upserted 12 rows.");
    }

    private void upsertMultiplier(String sec, String section, int wt, String rateAlias,
                                   double g1, double g2, double g3, double g4, double g5,
                                   double g6, double g7, double g8, double g9) {
        EffMultiplier m = multiplierRepo.findBySec(sec).orElseGet(EffMultiplier::new);
        m.setSec(sec);
        m.setSection(section);
        m.setWt(wt);
        m.setRateAlias(rateAlias);
        m.setGrade1(g1);
        m.setGrade2(g2);
        m.setGrade3(g3);
        m.setGrade4(g4);
        m.setGrade5(g5);
        m.setGrade6(g6);
        m.setGrade7(g7);
        m.setGrade8(g8);
        m.setGrade9(g9);
        multiplierRepo.save(m);
    }

    private void seedRates() {
        // Upsert by (sec, effPercent): correct existing rows to the exact Excel-source
        // values on every startup — same "keep in sync with source" policy as multipliers.
        existingRates = new HashMap<>();
        for (EffIncentiveRate x : rateRepo.findAll()) {
            existingRates.putIfAbsent(x.getSec() + "|" + x.getEffPercent(), x);
        }

        // ASSY10
        rateRepo.saveAll(List.of(
                r("ASSY10", "ASSY", 10, 0.0, 0.0),
                r("ASSY10", "ASSY", 10, 79.5, 8120.76),
                r("ASSY10", "ASSY", 10, 84.5, 9023.46),
                r("ASSY10", "ASSY", 10, 89.5, 9926.16),
                r("ASSY10", "ASSY", 10, 94.5, 10827.68),
                r("ASSY10", "ASSY", 10, 99.5, 11730.38),
                r("ASSY10", "ASSY", 10, 104.5, 12633.08),
                r("ASSY10", "ASSY", 10, 109.5, 13535.78),
                r("ASSY10", "ASSY", 10, 114.5, 14437.3),
                r("ASSY10", "ASSY", 10, 119.5, 15340.0)
        ));

        // ASSY8
        rateRepo.saveAll(List.of(
                r("ASSY8", "ASSY", 8, 0.0, 0.0),
                r("ASSY8", "ASSY", 8, 79.5, 5424.1),
                r("ASSY8", "ASSY", 8, 84.5, 6259.0),
                r("ASSY8", "ASSY", 8, 89.5, 7092.8),
                r("ASSY8", "ASSY", 8, 94.5, 7927.7),
                r("ASSY8", "ASSY", 8, 99.5, 8762.6),
                r("ASSY8", "ASSY", 8, 104.5, 9596.4),
                r("ASSY8", "ASSY", 8, 109.5, 10431.3),
                r("ASSY8", "ASSY", 8, 114.5, 11265.1),
                r("ASSY8", "ASSY", 8, 119.5, 12100.0)
        ));

        // ASSY4
        rateRepo.saveAll(List.of(
                r("ASSY4", "ASSY", 4, 0.0, 0.0),
                r("ASSY4", "ASSY", 4, 79.5, 2712.05),
                r("ASSY4", "ASSY", 4, 84.5, 3129.5),
                r("ASSY4", "ASSY", 4, 89.5, 3546.4),
                r("ASSY4", "ASSY", 4, 94.5, 3963.85),
                r("ASSY4", "ASSY", 4, 99.5, 4381.3),
                r("ASSY4", "ASSY", 4, 104.5, 4798.2),
                r("ASSY4", "ASSY", 4, 109.5, 5215.65),
                r("ASSY4", "ASSY", 4, 114.5, 5632.55),
                r("ASSY4", "ASSY", 4, 119.5, 6050.0)
        ));

        // SF10
        rateRepo.saveAll(List.of(
                r("SF10", "SF", 10, 0.0, 0.0),
                r("SF10", "SF", 10, 79.5, 5000.0),
                r("SF10", "SF", 10, 84.5, 7000.0),
                r("SF10", "SF", 10, 89.5, 9000.0),
                r("SF10", "SF", 10, 94.5, 11000.0),
                r("SF10", "SF", 10, 99.5, 13000.0),
                r("SF10", "SF", 10, 104.5, 15000.0),
                r("SF10", "SF", 10, 109.5, 17000.0),
                r("SF10", "SF", 10, 114.5, 19000.0),
                r("SF10", "SF", 10, 119.5, 21000.0)
        ));

        // SF8
        rateRepo.saveAll(List.of(
                r("SF8", "SF", 8, 0.0, 0.0),
                r("SF8", "SF", 8, 79.5, 4000.0),
                r("SF8", "SF", 8, 84.5, 5600.0),
                r("SF8", "SF", 8, 89.5, 7200.0),
                r("SF8", "SF", 8, 94.5, 8800.0),
                r("SF8", "SF", 8, 99.5, 10400.0),
                r("SF8", "SF", 8, 104.5, 12000.0),
                r("SF8", "SF", 8, 109.5, 13600.0),
                r("SF8", "SF", 8, 114.5, 15200.0),
                r("SF8", "SF", 8, 119.5, 16800.0)
        ));

        // SF4
        rateRepo.saveAll(List.of(
                r("SF4", "SF", 4, 0.0, 0.0),
                r("SF4", "SF", 4, 79.5, 2000.0),
                r("SF4", "SF", 4, 84.5, 2800.0),
                r("SF4", "SF", 4, 89.5, 3600.0),
                r("SF4", "SF", 4, 94.5, 4400.0),
                r("SF4", "SF", 4, 99.5, 5200.0),
                r("SF4", "SF", 4, 104.5, 6000.0),
                r("SF4", "SF", 4, 109.5, 6800.0),
                r("SF4", "SF", 4, 114.5, 7600.0),
                r("SF4", "SF", 4, 119.5, 8400.0)
        ));

        // SEW10 (17 rows, finer granularity)
        rateRepo.saveAll(List.of(
                r("SEW10", "SEW", 10, 0.0, 0.0),
                r("SEW10", "SEW", 10, 79.5, 3984.375),
                r("SEW10", "SEW", 10, 82.5, 4382.8125),
                r("SEW10", "SEW", 10, 84.5, 4781.25),
                r("SEW10", "SEW", 10, 87.5, 5179.6875),
                r("SEW10", "SEW", 10, 89.5, 5578.125),
                r("SEW10", "SEW", 10, 92.5, 5976.5625),
                r("SEW10", "SEW", 10, 94.5, 6375.0),
                r("SEW10", "SEW", 10, 97.5, 6773.4375),
                r("SEW10", "SEW", 10, 99.5, 7171.875),
                r("SEW10", "SEW", 10, 102.5, 7570.3125),
                r("SEW10", "SEW", 10, 104.5, 7968.75),
                r("SEW10", "SEW", 10, 109.5, 8367.1875),
                r("SEW10", "SEW", 10, 114.5, 8566.875),
                r("SEW10", "SEW", 10, 119.5, 8765.625),
                r("SEW10", "SEW", 10, 124.5, 8965.3125),
                r("SEW10", "SEW", 10, 129.5, 9164.0625)
        ));

        // SEW8 (17 rows)
        rateRepo.saveAll(List.of(
                r("SEW8", "SEW", 8, 0.0, 0.0),
                r("SEW8", "SEW", 8, 79.5, 3187.5),
                r("SEW8", "SEW", 8, 82.5, 3506.25),
                r("SEW8", "SEW", 8, 84.5, 3825.0),
                r("SEW8", "SEW", 8, 87.5, 4143.75),
                r("SEW8", "SEW", 8, 89.5, 4462.5),
                r("SEW8", "SEW", 8, 92.5, 4781.25),
                r("SEW8", "SEW", 8, 94.5, 5100.0),
                r("SEW8", "SEW", 8, 97.5, 5418.75),
                r("SEW8", "SEW", 8, 99.5, 5737.5),
                r("SEW8", "SEW", 8, 102.5, 6056.25),
                r("SEW8", "SEW", 8, 104.5, 6375.0),
                r("SEW8", "SEW", 8, 109.5, 6693.75),
                r("SEW8", "SEW", 8, 114.5, 6853.5),
                r("SEW8", "SEW", 8, 119.5, 7012.5),
                r("SEW8", "SEW", 8, 124.5, 7172.25),
                r("SEW8", "SEW", 8, 129.5, 7331.25)
        ));

        // SEW4 (17 rows)
        rateRepo.saveAll(List.of(
                r("SEW4", "SEW", 4, 0.0, 0.0),
                r("SEW4", "SEW", 4, 79.5, 1593.75),
                r("SEW4", "SEW", 4, 82.5, 1753.125),
                r("SEW4", "SEW", 4, 84.5, 1912.5),
                r("SEW4", "SEW", 4, 87.5, 2071.875),
                r("SEW4", "SEW", 4, 89.5, 2231.25),
                r("SEW4", "SEW", 4, 92.5, 2390.625),
                r("SEW4", "SEW", 4, 94.5, 2550.0),
                r("SEW4", "SEW", 4, 97.5, 2709.375),
                r("SEW4", "SEW", 4, 99.5, 2868.75),
                r("SEW4", "SEW", 4, 102.5, 3028.125),
                r("SEW4", "SEW", 4, 104.5, 3187.5),
                r("SEW4", "SEW", 4, 109.5, 3346.875),
                r("SEW4", "SEW", 4, 114.5, 3426.75),
                r("SEW4", "SEW", 4, 119.5, 3506.25),
                r("SEW4", "SEW", 4, 124.5, 3586.125),
                r("SEW4", "SEW", 4, 129.5, 3665.625)
        ));

        log.info("EffIncentiveRate: upserted rows from Excel source values.");
    }

    private Map<String, EffIncentiveRate> existingRates;

    private EffIncentiveRate r(String sec, String section, int wt, double effPct, double rate) {
        EffIncentiveRate e = existingRates.getOrDefault(sec + "|" + effPct, new EffIncentiveRate());
        e.setSec(sec);
        e.setSection(section);
        e.setWt(wt);
        e.setEffPercent(effPct);
        e.setRate(rate);
        return e;
    }
}

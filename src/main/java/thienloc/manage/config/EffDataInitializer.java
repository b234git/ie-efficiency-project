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

import java.util.List;

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
        upsertMultiplier("SEW8",   "SEW",  8,  null,   1.0, 1.33, 2.93, 3.17, 4.72, 5.04, 4.96, 5.19, 6.14);
        upsertMultiplier("SEW10",  "SEW",  10, null,   1.0, 1.33, 2.93, 3.17, 4.72, 5.04, 4.96, 5.19, 6.14);
        upsertMultiplier("SEW4",   "SEW",  4,  null,   1.0, 1.33, 2.93, 3.17, 4.72, 5.04, 4.96, 5.19, 6.14);
        upsertMultiplier("ASSY8",  "ASSY", 8,  null,   1.0, 1.20, 1.50, 2.03, 2.73, 2.94, 3.34, 3.65, 3.95);
        upsertMultiplier("ASSY10", "ASSY", 10, null,   1.0, 1.20, 1.50, 2.03, 2.73, 2.94, 3.34, 3.65, 3.95);
        upsertMultiplier("ASSY4",  "ASSY", 4,  null,   1.0, 1.20, 1.50, 2.03, 2.73, 2.94, 3.34, 3.65, 3.95);
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
        if (rateRepo.count() > 0) {
            log.info("EffIncentiveRate: data already exists, skipping seed.");
            return;
        }

        // ASSY10
        rateRepo.saveAll(List.of(
                r("ASSY10", "ASSY", 10, 0.0, 0.0),
                r("ASSY10", "ASSY", 10, 79.5, 8121.0),
                r("ASSY10", "ASSY", 10, 84.5, 9023.0),
                r("ASSY10", "ASSY", 10, 89.5, 9926.0),
                r("ASSY10", "ASSY", 10, 94.5, 10828.0),
                r("ASSY10", "ASSY", 10, 99.5, 11730.0),
                r("ASSY10", "ASSY", 10, 104.5, 12633.0),
                r("ASSY10", "ASSY", 10, 109.5, 13536.0),
                r("ASSY10", "ASSY", 10, 114.5, 14437.0),
                r("ASSY10", "ASSY", 10, 119.5, 15340.0)
        ));

        // ASSY8
        rateRepo.saveAll(List.of(
                r("ASSY8", "ASSY", 8, 0.0, 0.0),
                r("ASSY8", "ASSY", 8, 79.5, 5424.0),
                r("ASSY8", "ASSY", 8, 84.5, 6259.0),
                r("ASSY8", "ASSY", 8, 89.5, 7093.0),
                r("ASSY8", "ASSY", 8, 94.5, 7928.0),
                r("ASSY8", "ASSY", 8, 99.5, 8763.0),
                r("ASSY8", "ASSY", 8, 104.5, 9596.0),
                r("ASSY8", "ASSY", 8, 109.5, 10431.0),
                r("ASSY8", "ASSY", 8, 114.5, 11265.0),
                r("ASSY8", "ASSY", 8, 119.5, 12100.0)
        ));

        // ASSY4
        rateRepo.saveAll(List.of(
                r("ASSY4", "ASSY", 4, 0.0, 0.0),
                r("ASSY4", "ASSY", 4, 79.5, 2712.0),
                r("ASSY4", "ASSY", 4, 84.5, 3130.0),
                r("ASSY4", "ASSY", 4, 89.5, 3546.0),
                r("ASSY4", "ASSY", 4, 94.5, 3964.0),
                r("ASSY4", "ASSY", 4, 99.5, 4381.0),
                r("ASSY4", "ASSY", 4, 104.5, 4798.0),
                r("ASSY4", "ASSY", 4, 109.5, 5216.0),
                r("ASSY4", "ASSY", 4, 114.5, 5633.0),
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
                r("SEW10", "SEW", 10, 79.5, 3984.0),
                r("SEW10", "SEW", 10, 82.5, 4383.0),
                r("SEW10", "SEW", 10, 84.5, 4781.0),
                r("SEW10", "SEW", 10, 87.5, 5180.0),
                r("SEW10", "SEW", 10, 89.5, 5578.0),
                r("SEW10", "SEW", 10, 92.5, 5977.0),
                r("SEW10", "SEW", 10, 94.5, 6375.0),
                r("SEW10", "SEW", 10, 97.5, 6773.0),
                r("SEW10", "SEW", 10, 99.5, 7172.0),
                r("SEW10", "SEW", 10, 102.5, 7570.0),
                r("SEW10", "SEW", 10, 104.5, 7969.0),
                r("SEW10", "SEW", 10, 109.5, 8367.0),
                r("SEW10", "SEW", 10, 114.5, 8567.0),
                r("SEW10", "SEW", 10, 119.5, 8766.0),
                r("SEW10", "SEW", 10, 124.5, 8965.0),
                r("SEW10", "SEW", 10, 129.5, 9164.0)
        ));

        // SEW8 (17 rows)
        rateRepo.saveAll(List.of(
                r("SEW8", "SEW", 8, 0.0, 0.0),
                r("SEW8", "SEW", 8, 79.5, 3188.0),
                r("SEW8", "SEW", 8, 82.5, 3506.0),
                r("SEW8", "SEW", 8, 84.5, 3825.0),
                r("SEW8", "SEW", 8, 87.5, 4144.0),
                r("SEW8", "SEW", 8, 89.5, 4463.0),
                r("SEW8", "SEW", 8, 92.5, 4781.0),
                r("SEW8", "SEW", 8, 94.5, 5100.0),
                r("SEW8", "SEW", 8, 97.5, 5419.0),
                r("SEW8", "SEW", 8, 99.5, 5738.0),
                r("SEW8", "SEW", 8, 102.5, 6056.0),
                r("SEW8", "SEW", 8, 104.5, 6375.0),
                r("SEW8", "SEW", 8, 109.5, 6694.0),
                r("SEW8", "SEW", 8, 114.5, 6854.0),
                r("SEW8", "SEW", 8, 119.5, 7013.0),
                r("SEW8", "SEW", 8, 124.5, 7172.0),
                r("SEW8", "SEW", 8, 129.5, 7331.0)
        ));

        // SEW4 (17 rows)
        rateRepo.saveAll(List.of(
                r("SEW4", "SEW", 4, 0.0, 0.0),
                r("SEW4", "SEW", 4, 79.5, 1594.0),
                r("SEW4", "SEW", 4, 82.5, 1753.0),
                r("SEW4", "SEW", 4, 84.5, 1913.0),
                r("SEW4", "SEW", 4, 87.5, 2072.0),
                r("SEW4", "SEW", 4, 89.5, 2231.0),
                r("SEW4", "SEW", 4, 92.5, 2391.0),
                r("SEW4", "SEW", 4, 94.5, 2550.0),
                r("SEW4", "SEW", 4, 97.5, 2709.0),
                r("SEW4", "SEW", 4, 99.5, 2869.0),
                r("SEW4", "SEW", 4, 102.5, 3028.0),
                r("SEW4", "SEW", 4, 104.5, 3188.0),
                r("SEW4", "SEW", 4, 109.5, 3347.0),
                r("SEW4", "SEW", 4, 114.5, 3427.0),
                r("SEW4", "SEW", 4, 119.5, 3506.0),
                r("SEW4", "SEW", 4, 124.5, 3586.0),
                r("SEW4", "SEW", 4, 129.5, 3666.0)
        ));

        log.info("EffIncentiveRate: seeded 97 rows.");
    }

    private EffIncentiveRate r(String sec, String section, int wt, double effPct, double rate) {
        return EffIncentiveRate.builder()
                .sec(sec).section(section).wt(wt)
                .effPercent(effPct).rate(rate)
                .build();
    }
}

package thienloc.manage.service;

import thienloc.manage.dto.DailyProductionDto;
import thienloc.manage.entity.DailyProduction;

import java.util.List;

/**
 * Contract for efficiency metric population.
 * Allows {@link ProductionService} and tests to depend on an abstraction
 * rather than the concrete {@link EfficiencyCalculatorService}.
 */
public interface IEfficiencyCalculatorService {

    void populateEfficiencyMetrics(DailyProductionDto dto, DailyProduction entity);

    void populateEfficiencyMetricsBatch(List<DailyProductionDto> dtos, List<DailyProduction> entities);
}

package thienloc.manage.service;

import thienloc.manage.dto.SalaryReportDto;

/**
 * Contract for salary/incentive report generation.
 */
public interface ISalaryService {

    SalaryReportDto buildReport(String month);
}

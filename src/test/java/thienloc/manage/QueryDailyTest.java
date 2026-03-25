package thienloc.manage;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class QueryDailyTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void runQuery() throws Exception {
        System.out.println(">>> QUERY DAILY <<<");
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT dp.* FROM daily_production dp JOIN daily_production_details dpd ON dp.id = dpd.daily_production_id WHERE dpd.article_no LIKE '%398846%' AND dp.production_date = '2026-02-02'");
        for (Map<String, Object> row : rows) {
            System.out.println(row);
        }
        System.out.println(">>> END QUERY <<<");
    }
}

package thienloc.manage;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.List;
import java.util.Map;

@Disabled("Ad-hoc debug query — requires live PostgreSQL connection")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public class QueryMasterDbTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    public void runQuery() throws Exception {
        System.out.println(">>> QUERY MASTER DB <<<");
        List<Map<String, Object>> rows = jdbcTemplate
                .queryForList("SELECT * FROM master_db WHERE article_no LIKE '%398846%'");
        for (Map<String, Object> row : rows) {
            System.out.println(row);
        }
        System.out.println(">>> END QUERY <<<");
    }
}

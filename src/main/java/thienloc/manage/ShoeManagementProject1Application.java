package thienloc.manage;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ShoeManagementProject1Application {

	public static void main(String[] args) {
		// POI caps single zip-part byte arrays at 100MB (anti zip-bomb). Large but legitimate
		// EFF workbooks (e.g. EFF JUNE V6) have a ~123MB uncompressed part and trip this guard.
		// ponytail: 256MB ceiling; if a future file exceeds it, switch the importer to POI's
		// streaming reader (XSSFReader/SAX) instead of raising this further.
		org.apache.poi.util.IOUtils.setByteArrayMaxOverride(256 * 1024 * 1024);
		SpringApplication.run(ShoeManagementProject1Application.class, args);
	}

}

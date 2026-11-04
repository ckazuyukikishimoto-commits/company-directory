package com.example.company_directory;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// テストで本番の DB につないでいても、ゴミ箱のデータを消さないよう起動時パージは止める
@SpringBootTest(properties = "app.trash.purge-on-startup=false")
class CompanyDirectoryApplicationTests {

	@Test
	void contextLoads() {
	}

}

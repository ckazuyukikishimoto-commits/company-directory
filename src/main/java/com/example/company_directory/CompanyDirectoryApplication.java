package com.example.company_directory;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 企業名簿管理アプリケーションのエントリーポイント。
 * Spring Bootアプリケーションの起動を担当し、定期的なゴミ箱クリーンアップなどの機能を有効化します。
 */
@SpringBootApplication
@EnableScheduling
public class CompanyDirectoryApplication {

	public static void main(String[] args) {
		SpringApplication.run(CompanyDirectoryApplication.class, args);
	}

}

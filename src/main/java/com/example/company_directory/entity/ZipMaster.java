package com.example.company_directory.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Entity
@Table(name = "zip_master")
@Data
public class ZipMaster {
    @Id
    @Column(length = 7)
    private String zipCode; // ハイフンなし7桁 (例: 1000005)

    @Column(length = 20)
    private String prefecture; // 都道府県 (例: 東京都)

    @Column(length = 50)
    private String city;       // 市区町村 (例: 千代田区)

    @Column(length = 100)
    private String town;       // 町域 (例: 丸の内)
}

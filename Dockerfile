# 1. ベースイメージの指定 (Java 17)
FROM amazoncorretto:21-alpine-jdk

# 2. 作業ディレクトリを作成
WORKDIR /app

# 3. ビルドされたJARファイルをコンテナ内にコピー
# (Gradleでビルドすると build/libs/ に生成されます)
COPY build/libs/*.jar app.jar

# 4. アプリケーションの実行
ENTRYPOINT ["java", "-jar", "app.jar"]
# DRIVE 도메인 트러블슈팅

---

### 1. MinIO 의존성 로딩 실패 (Spring Boot 3 의존성 충돌)

**증상**
```
MinIO Bean이 생성되지 않거나 의존성 충돌로 빌드 실패
```

**원인**
MinIO 8.5.x는 내부적으로 `okhttp3`, `guava`를 사용하는데 Spring Boot 3 환경에서 버전 충돌 발생.

**해결**
```groovy
implementation('io.minio:minio:8.5.7') {
    exclude group: 'com.google.guava', module: 'guava'
    exclude group: 'com.squareup.okhttp3', module: 'okhttp'
}
implementation 'com.google.guava:guava:32.1.3-jre'
implementation 'com.squareup.okhttp3:okhttp:4.12.0'
```

---

### 2. CI에서 MinIO 환경변수 미설정으로 contextLoads() 실패

**증상**
```
AttendanceApplicationTests > contextLoads() FAILED
    Caused by: org.springframework.util.PlaceholderResolutionException
```

**원인**
DRIVE 도메인 추가로 `${MINIO_ENDPOINT}` 등 플레이스홀더가 추가됐는데 CI 환경에 환경변수가 없어 Context 로딩 실패.

**해결**
`src/test/resources/application.properties`에 테스트용 기본값 추가.
```properties
minio.endpoint=http://localhost:9000
minio.access-key=test
minio.secret-key=testpassword
minio.bucket=test-bucket
```

---

### 3. DriveFile 엔티티에 bucket 컬럼 불필요

**원인**
버킷명은 `application.properties`의 `minio.bucket`으로 중앙 관리되기 때문에 파일별로 DB에 저장할 필요가 없음.

**해결**
`DriveFile` 엔티티와 V11 마이그레이션에서 `bucket` 컬럼 제거.

---

### 4. DriveFileController GET 중복 매핑으로 contextLoads() 실패

**증상**
```
Caused by: java.lang.IllegalStateException
  at AbstractHandlerMethodMapping.java:677
```

**원인**
`getAllFiles()` 메서드 추가 시 기존 `getMyFiles()`를 제거하지 않아
`GET /api/v1/drive`에 `@GetMapping`이 두 개 존재.

**해결**
`getMyFiles()` 메서드 제거 후 `getAllFiles()` 하나만 유지.

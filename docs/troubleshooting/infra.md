# 인프라 트러블슈팅 (Hyper-V / Ubuntu Server)

---

### 1. Ubuntu Desktop ISO로 설치 시 검은 화면에서 멈춤

**증상**
Hyper-V에서 `Try or Install Ubuntu` 선택 후 검은 화면과 `X` 커서만 보이고 멈춤.

**원인**
Desktop ISO는 Hyper-V 환경에서 그래픽 초기화 문제가 발생할 수 있음.

**해결**
```
Before: ubuntu-24.04.4-desktop-amd64.iso
After : ubuntu-24.04.4-live-server-amd64.iso
```

---

### 2. VM 부팅은 되지만 설치 화면 진입 실패

**증상**
VM 실행 후 검은 화면만 보이거나 설치 화면으로 정상 진입하지 못함.

**원인**
- DVD 드라이브에 ISO가 제대로 연결되지 않음
- Secure Boot 설정이 Ubuntu와 맞지 않음
- 부팅 우선순위에서 DVD가 앞에 오지 않음

**해결**
1. 설정 → SCSI 컨트롤러 → DVD 드라이브에서 ISO 파일 연결 확인
2. 설정 → 보안에서 Secure Boot 템플릿 조정
3. 설정 → 펌웨어에서 DVD Drive를 부팅 우선순위 상단으로 배치

---

### 3. Secure Boot 설정으로 인해 Ubuntu 부팅 불안정

**원인**
Hyper-V 기본 Secure Boot 설정은 Windows 중심이라 Ubuntu와 궁합이 맞지 않을 수 있음.

**해결**
설정 → 보안에서 Secure Boot 템플릿을 `Microsoft UEFI Certificate Authority`로 변경.

---

### 4. 외부 가상 스위치에 잘못된 NIC 연결

**증상**
Ubuntu 설치 후 네트워크가 잡히지 않거나 인터넷 연결이 되지 않음.

**원인**
외부 가상 스위치를 만들 때 실제 인터넷이 연결된 물리 NIC가 아닌 연결이 끊어진 어댑터를 선택.

**해결**
실제 DHCP와 IP가 잡혀 있는 NIC를 외부 스위치에 연결.

---

### 5. VM 디스크를 기본 경로가 아닌 SSD에 저장

**해결**
VM 생성 과정의 가상 하드 디스크 연결 단계에서 위치를 SSD 경로로 직접 변경.
```
예시: D:\Hyper-V\app-server\app-server.vhdx
```

---

### 6. Ubuntu Pro 활성화 화면이 설치 오류처럼 보임

**증상**
설치 도중 `Upgrade to Ubuntu Pro` 화면이 나타나 설치가 멈춘 것처럼 보임.

**원인**
정상 설치 흐름의 일부.

**해결**
`Skip for now` → `Continue` 선택.

---

### 7. GitHub SSH 키 자동 import 실패

**증상**
```
Importing keys failed:
ERROR No matching keys found for [gh:<GitHub-username>]
```

**원인**
해당 GitHub 계정에 등록된 SSH 공개키가 없었음.

**해결**
해당 단계 무시 후 설치 완료 뒤 `~/.ssh/authorized_keys`에 직접 등록.

---

### 8. 1대 vs 2대 구성 인프라 설계 판단

**결론**
포트폴리오 목적을 고려해 2대 구성으로 방향 결정.

| 서버 | 구성 요소 |
|------|-----------|
| app-server | Nginx, Spring Boot, GitHub Actions Runner |
| data-server | PostgreSQL, Redis, MinIO, Grafana / Prometheus |

---

### 9. Redis / MinIO / Grafana 도입 명분 정리

| 기술 | 도입 이유 |
|------|-----------|
| Redis | 채팅, 캐시, Pub/Sub, 임시 상태값 저장 |
| MinIO | 파일 업로드, 객체 스토리지, S3 유사 구조 |
| Grafana / Prometheus | 운영 지표 관측 |
| GitHub Actions | CI/CD 자동화 |
